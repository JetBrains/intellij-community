// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.internal.ml.catboost

import com.intellij.internal.ml.InconsistentMetadataException
import com.intellij.internal.ml.ResourcesModelMetadataReader
import com.intellij.openapi.util.IntellijInternalApi

class CatBoostResourcesModelMetadataReader(metadataHolder: Class<*>,
                                           featuresDirectory: String,
                                           private val modelDirectory: String) : ResourcesModelMetadataReader(metadataHolder, featuresDirectory), CatBoostModelMetadataReader {

  override fun loadModel(): NaiveCatBoostModel {
    val resource = "$modelDirectory/model.bin"
    val fileStream = metadataHolder.classLoader.getResourceAsStream(resource)
                     ?: throw InconsistentMetadataException(
                       "Metadata file not found: $resource. Resources holder: ${metadataHolder.name}")
    return NaiveCatBoostModel.loadModel(fileStream)
  }
}