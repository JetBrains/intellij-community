// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

open class ResourcesModelMetadataReader(protected val metadataHolder: Class<*>, private val featuresDirectory: String): ModelMetadataReader {

  override fun binaryFeatures(): String = resourceContent("binary.json")
  override fun floatFeatures(): String = resourceContent("float.json")
  override fun categoricalFeatures(): String = resourceContent("categorical.json")
  override fun allKnown(): String = resourceContent("all_features.json")
  override fun featureOrderDirect(): List<String> = resourceContent("features_order.txt").lines()

  override fun extractVersion(): String? {
    val versionFile = "version.txt"
    if (metadataHolder.classLoader.getResource("$featuresDirectory/$versionFile") != null) {
      return resourceContent(versionFile).trim()
    }
    val resource = metadataHolder.classLoader.getResource("$featuresDirectory/binary.json") ?: return null
    val result = resource.file.substringBeforeLast(".jar!", "").substringAfterLast("-", "")
    return if (result.isBlank()) null else result
  }

  private fun resourceContent(fileName: String): String {
    val resource = "$featuresDirectory/$fileName"
    val fileStream = metadataHolder.classLoader.getResourceAsStream(resource)
                     ?: throw InconsistentMetadataException(
                       "Metadata file not found: $resource. Resources holder: ${metadataHolder.name}")
    return fileStream.bufferedReader().use { it.readText() }
  }
}
