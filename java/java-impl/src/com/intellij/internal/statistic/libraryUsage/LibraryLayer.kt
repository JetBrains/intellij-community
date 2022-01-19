// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.libraryUsage

import com.intellij.openapi.diagnostic.thisLogger

class LibraryLayer private constructor(
  /**
   * Library on this layer. Must be no more than one per layer.
   */
  private val libraryName: String? = null,

  /**
   * Nested layer
   */
  private val nextLayers: Map<String, LibraryLayer> = emptyMap(),
) : LibraryDescriptorFinder {
  override fun findSuitableLibrary(packageQualifier: String): String? = findLibrary(packageQualifier)

  private fun findLibrary(packageQualifier: String?): String? {
    if (packageQualifier == null) return libraryName

    val (key, newPrefix) = packageQualifier.splitByDot()
    return nextLayers[key]?.findLibrary(newPrefix) ?: libraryName
  }

  companion object {
    fun create(libraryDescriptors: List<LibraryDescriptor>): LibraryLayer = LibraryLayerBuilder().apply {
      for (descriptor in libraryDescriptors) {
        add(descriptor.packagePrefix, descriptor.libraryName)
      }
    }.toLibraryLayer()

    private fun String.splitByDot(): Pair<String, String?> = indexOf('.').takeUnless { it == -1 }?.let { indexOfDelimiter ->
      substring(0, indexOfDelimiter) to substring(indexOfDelimiter + 1)
    } ?: (this to null)
  }

  private class LibraryLayerBuilder {
    var libraryName: String? = null
    val nextLayers: MutableMap<String, LibraryLayerBuilder> = mutableMapOf()

    fun add(packagePrefix: String?, libraryName: String) {
      if (packagePrefix == null) {
        if (this.libraryName != null) {
          thisLogger().warn("'${this.libraryName}' library will be replaced with '$libraryName' library")
        }

        this.libraryName = libraryName
        return
      }

      val (key, newPrefix) = packagePrefix.splitByDot()
      nextLayers.getOrPut(key, ::LibraryLayerBuilder).add(newPrefix, libraryName)
    }

    fun toLibraryLayer(): LibraryLayer = LibraryLayer(
      libraryName = libraryName,
      nextLayers = nextLayers.mapValues { it.value.toLibraryLayer() },
    )
  }
}