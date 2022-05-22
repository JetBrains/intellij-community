// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.ml.models.local

import com.intellij.internal.ml.ModelMetadataReader
import com.intellij.internal.ml.catboost.NaiveCatBoostModel
import java.io.File

class LocalCatBoostModelMetadataReader(private val modelDirectory: String,
                                       private val featuresDirectory: String) : ModelMetadataReader {
  override fun binaryFeatures() = readFeaturesFile("binary.json")
  override fun floatFeatures() = readFeaturesFile("float.json")
  override fun categoricalFeatures() = readFeaturesFile("categorical.json")
  override fun allKnown() = readFeaturesFile("all_features.json")
  override fun featureOrderDirect() = readFeaturesFile("features_order.txt").lines()
  override fun extractVersion(): String? = null

  fun loadModel(): NaiveCatBoostModel {
    val modelInputStream = File("$modelDirectory/model.bin").inputStream()
    return NaiveCatBoostModel.loadModel(modelInputStream)
  }

  private fun readFeaturesFile(filename: String): String {
    val absolutePathToFile = File("$modelDirectory/$featuresDirectory/$filename")
    return absolutePathToFile.readText()
  }
}