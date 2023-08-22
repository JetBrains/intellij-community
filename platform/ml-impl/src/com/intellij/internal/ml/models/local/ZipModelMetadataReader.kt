// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml.models.local

import com.intellij.internal.ml.ModelMetadataReader
import java.io.InputStream
import java.util.zip.ZipFile

open class ZipModelMetadataReader(private val zipFile: ZipFile): ModelMetadataReader {
  override fun binaryFeatures(): String = resourceContent("binary.json")
  override fun floatFeatures(): String = resourceContent("float.json")
  override fun categoricalFeatures(): String = resourceContent("categorical.json")
  override fun allKnown(): String = resourceContent("all_features.json")
  override fun featureOrderDirect(): List<String> = resourceContent("features_order.txt").lines()

  override fun extractVersion(): String? = null

  fun tryGetResourceAsStream(fileName: String): InputStream? {
    val entry = zipFile.entries().asSequence().firstOrNull { it.name.endsWith(fileName) } ?: return null
    return zipFile.getInputStream(entry)
  }

  fun resourceContent(fileName: String): String {
    val stream = tryGetResourceAsStream(fileName)
    if (stream == null)
      throw IllegalStateException("Can't find necessary '${fileName}' resource in zip file")

    return stream.bufferedReader().use { it.readText() }
  }
}
