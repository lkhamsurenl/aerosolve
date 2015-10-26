package com.airbnb.aerosolve.training

import java.io.{StringReader, BufferedWriter, BufferedReader, StringWriter}

import com.airbnb.aerosolve.core.models.ModelFactory
import com.airbnb.aerosolve.core.{Example, FeatureVector}
import com.typesafe.config.ConfigFactory
import org.apache.spark.SparkContext
import org.junit.Test
import org.slf4j.LoggerFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import scala.collection.JavaConverters._

import scala.collection.mutable.ArrayBuffer

class DecisionTreeModelTest {
  val log = LoggerFactory.getLogger("DecisionTreeModelTest")

  def makeConfig(splitCriteria : String) : String = {
    """
      |identity_transform {
      |  transform : list
      |  transforms: []
      |}
      |model_config {
      |  rank_key : "$rank"
      |  split_criteria : "%s"
      |  num_candidates : 1000
      |  rank_threshold : 0.0
      |  max_depth : 5
      |  min_leaf_items : 5
      |  num_tries : 10
      |  context_transform : identity_transform
      |  item_transform : identity_transform
      |  combined_transform : identity_transform
      |}
    """.stripMargin
       .format(splitCriteria)
  }

  /*
  @Test
  def testDecisionTreeTrainerHellinger() = {
    testDecisionTreeClassificationTrainer("hellinger", 0.8)
  }

  @Test
  def testDecisionTreeTrainerGini() = {
    testDecisionTreeClassificationTrainer("gini", 0.8)
  }

  @Test
  def testDecisionTreeTrainerInformationGain() = {
    testDecisionTreeClassificationTrainer("information_gain", 0.8)
  }
  */

  @Test
  def testDecisionTreeTrainerVariance() = {
    testDecisionTreeRegressionTrainer("variance")
  }

  def testDecisionTreeClassificationTrainer(splitCriteria : String,
                                            expectedCorrect : Double) = {
    val (examples, label, numPos) = TrainingTestHelper.makeClassificationExamples

    var sc = new SparkContext("local", "DecisionTreeModelTest")

    try {
      val config = ConfigFactory.parseString(makeConfig(splitCriteria))

      val input = sc.parallelize(examples)
      val model = DecisionTreeTrainer.train(sc, input, config, "model_config")

      val stumps = model.getStumps.asScala
      stumps.foreach(stump => log.info(stump.toString))

      var numCorrect : Int = 0
      var i : Int = 0
      val labelArr = label.toArray
      for (ex <- examples) {
        val score = model.scoreItem(ex.example.get(0))
        if (score * labelArr(i) > 0) {
          numCorrect += 1
        }
        i += 1
      }
      val fracCorrect : Double = numCorrect * 1.0 / examples.length
      log.info("Num correct = %d, frac correct = %f, num pos = %d, num neg = %d"
                 .format(numCorrect, fracCorrect, numPos, examples.length - numPos))
      assertTrue(fracCorrect > expectedCorrect)

      val swriter = new StringWriter()
      val writer = new BufferedWriter(swriter)
      model.save(writer)
      writer.close()
      val str = swriter.toString()
      val sreader = new StringReader(str)
      val reader = new BufferedReader(sreader)

      val model2Opt = ModelFactory.createFromReader(reader)
      assertTrue(model2Opt.isPresent())
      val model2 = model2Opt.get()

      for (ex <- examples) {
        val score = model.scoreItem(ex.example.get(0))
        val score2 = model2.scoreItem(ex.example.get(0))
        assertEquals(score, score2, 0.01f)
      }

   } finally {
      sc.stop
      sc = null
      // To avoid Akka rebinding to the same port, since it doesn't unbind immediately on shutdown
      System.clearProperty("spark.master.port")
    }
  }

  def testDecisionTreeRegressionTrainer(splitCriteria : String) = {
    val examples = TrainingTestHelper.makeRegressionExamples



    var sc = new SparkContext("local", "DecisionTreeModelTest")

    try {
      val config = ConfigFactory.parseString(makeConfig(splitCriteria))

      val input = sc.parallelize(examples)
      val model = DecisionTreeTrainer.train(sc, input, config, "model_config")

      val stumps = model.getStumps.asScala
      stumps.foreach(stump => log.info(stump.toString))
    } finally {
      sc.stop
      sc = null

      // To avoid Akka rebinding to the same port, since it doesn't unbind immediately on shutdown
      System.clearProperty("spark.master.port")
    }
  }
}
