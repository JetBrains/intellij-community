// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.kotlin.KotlinSubclassesIndex
import org.jetbrains.jps.dependency.kotlin.LookupsIndex

const val VERSION = 4

internal fun prepareSerializedData(graph: DependencyGraph): ByteArray {
  val serializedDataResult = ByteArrayOutputStream().use { byteStream ->
    DataOutputStream(byteStream).use { dataOut ->
      dataOut.writeVersion()

      // process subtypes
      val subclassesIndex: BackDependencyIndex? = graph.getIndex(KotlinSubclassesIndex.NAME)
      val subclassesFilteredKeys = subclassesIndex?.keys
        ?.filter { key -> subclassesIndex.getDependencies(key).iterator().hasNext() }
      val subclassesCount = subclassesFilteredKeys?.count() ?: 0
      dataOut.writeInt(subclassesCount)
      subclassesFilteredKeys
        ?.sortedBy { it.toFqName() }
        ?.forEach { key ->
          val dependencies = subclassesIndex.getDependencies(key).map { it.toFqName() }.sorted()
          dataOut.writeUTF(key.toFqName())
          dataOut.writeInt(dependencies.size)
          dependencies.forEach { dataOut.writeUTF(it) }
        }

      // process lookups
      val lookupsIndex: BackDependencyIndex? = graph.getIndex(LookupsIndex.NAME)
      val lookupsCount = lookupsIndex?.keys?.count() ?: 0
      val fileIdToPathEntryAccumulator = mutableMapOf<String, Int>()
      dataOut.writeInt(lookupsCount)
      lookupsIndex?.keys
        ?.sortedBy { it.toFqName() }
        ?.forEach { key ->
          val dependencies = lookupsIndex.getDependencies(key)
            .mapNotNull { (it as? JvmNodeReferenceID)?.nodeName }
            .sorted()
          dataOut.writeUTF(key.toFqName())
          dataOut.writeInt(dependencies.count())
          dependencies
            .map { dependency -> dependency.addFilePathIfNeeded(fileIdToPathEntryAccumulator) }
            .forEach { indexedDependency ->
              dataOut.writeInt(indexedDependency)
            }
        }

      dataOut.writeInt(fileIdToPathEntryAccumulator.size)
      fileIdToPathEntryAccumulator.toSortedMap().forEach { (path, fileId) ->
        dataOut.writeInt(fileId)
        dataOut.writeUTF(path)
      }
    }
    byteStream.toByteArray()
  }
  return serializedDataResult
}

/**
 * Converts a JVM reference ID to a dot-separated fully qualified name.
 *
 * Examples:
 * - `com/example/Foo` → `com.example.Foo`
 * - `com/example/Foo$Bar` → `com.example.Foo.Bar`
 * - `com/example/Foo$1` → `com.example.Foo`
 * - `com/example/Foo$$Lambda$3` → `com.example.Foo.$Lambda`
 */
private fun ReferenceID.toFqName(): String {
  val jvmName = (this as? JvmNodeReferenceID)?.nodeName ?: return toString()

  return jvmName
    .replace('/', '.')
    .replace("$$", DOUBLE_DOLLAR_PLACEHOLDER)
    .replace(DOLLAR_DIGITS_SUFFIX_REGEX, "")
    .replace('$', '.')
    .replace(DOUBLE_DOLLAR_PLACEHOLDER, ".$")
}

private val DOLLAR_DIGITS_SUFFIX_REGEX = Regex("(?:\\$\\d+)+$")
private const val DOUBLE_DOLLAR_PLACEHOLDER = "\u0000DOUBLE_DOLLAR\u0000"

private fun String.addFilePathIfNeeded(fileIdToPathEntryAccumulator: MutableMap<String, Int>): Int {
  return fileIdToPathEntryAccumulator.computeIfAbsent(this) { fileIdToPathEntryAccumulator.size + 1 }
}

private fun DataOutputStream.writeVersion() = writeInt(VERSION)