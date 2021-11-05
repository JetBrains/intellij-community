package com.intellij.internal.ml.models.local

import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeaturesInfo
import com.intellij.internal.ml.completion.CompletionRankingModelBase

private class Tree(val thresholds: List<Double>,
                   val values: List<Double>,
                   val features: List<Int>,
                   val left: List<Int>,
                   val right: List<Int>) {
  private fun traverse(node: Int, featuresValues: DoubleArray): Double {
    assert (node != -1)
    return if (left[node] != -1) {
      val featureId = features[node]
      val featureValue = featuresValues[featureId]
      val threshold = thresholds[node]
      if (featureValue <= threshold) {
        traverse(left[node], featuresValues)
      }
      else {
        traverse(right[node], featuresValues)
      }
    }
    else {
      values[node]
    }
  }

  fun predict(featuresValues: DoubleArray): Double {
    return traverse(0, featuresValues)
  }
}

private class TreesModel {
  private val trees: ArrayList<Tree> = ArrayList()

  fun addTree(thresholds: List<Double>,
              values: List<Double>,
              features: List<Int>,
              left: List<Int>,
              right: List<Int>) {
    trees.add(Tree(thresholds, values, features, left, right))
  }

  fun predict(featuresValues: DoubleArray?): Double {
    if (featuresValues == null) return 0.0
    val koef = 1.0 / trees.size
    val sum = trees.stream()
      .mapToDouble { it.predict(featuresValues) }
      .sum()
    return sum * koef
  }
}

class LocalRandomForestModel private constructor(metadata: FeaturesInfo, private val treesModel: TreesModel)
  : CompletionRankingModelBase(metadata) {
  override fun predict(features: DoubleArray?): Double = treesModel.predict(features)

  companion object {
    fun loadModel(modelText: String, metadata: FeaturesInfo): DecisionFunction {
      val forest = readTreesModel(modelText)
      return LocalRandomForestModel(metadata, forest)
    }

    private fun readTreesModel(modelText: String): TreesModel {
      val reader = modelText.reader().buffered()
      val treesModel = TreesModel()
      val numberOfTrees = reader.readLine().toInt()
      for (i in 0 until numberOfTrees) {
        val left = reader.readLine().split(" ").map { it.toInt() }
        val right = reader.readLine().split(" ").map { it.toInt() }
        val thresholds = reader.readLine().split(" ").map { it.toDouble() }
        val features = reader.readLine().split(" ").map { it.toInt() }
        val values = reader.readLine().split(" ").map { it.toDouble() }
        treesModel.addTree(thresholds, values, features, left, right)
      }
      return treesModel
    }
  }
}