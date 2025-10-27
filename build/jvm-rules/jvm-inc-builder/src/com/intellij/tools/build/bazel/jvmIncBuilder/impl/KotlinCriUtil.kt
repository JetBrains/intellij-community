// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import org.jetbrains.jps.dependency.BackDependencyIndex
import org.jetbrains.jps.dependency.DependencyGraph
import org.jetbrains.jps.dependency.ReferenceID
import org.jetbrains.jps.dependency.java.JvmNodeReferenceID
import org.jetbrains.jps.dependency.java.SubclassesIndex
import org.jetbrains.jps.dependency.kotlin.LookupsIndex

const val VERSION = 1

internal fun prepareSerializedData(graph: DependencyGraph): Map<String, ByteArray> {
  val serializedDataResult = mutableMapOf<String, ByteArray>()

  // process subtypes
  val subclassesIndex: BackDependencyIndex? = graph.getIndex(SubclassesIndex.NAME)
  val subclassesCount = subclassesIndex?.keys?.count() ?: 0
  if (subclassesCount > 0) {
    ByteArrayOutputStream().use { byteStream ->
      DataOutputStream(byteStream).use { dataOut ->
        dataOut.writeVersion()
        dataOut.writeInt(subclassesCount)
        subclassesIndex?.keys?.forEach { key ->
          val dependencies = subclassesIndex.getDependencies(key).map { it.toFqName() }
          dataOut.writeUTF(key.toFqName())
          dataOut.writeInt(dependencies.size)
          dependencies.forEach { dataOut.writeUTF(it) }
        }
      }
      byteStream.toByteArray()
    }.also { serializedDataResult["subtypes"] = it }
  }

  // process lookups
  val lookupsIndex: BackDependencyIndex? = graph.getIndex(LookupsIndex.NAME)
  val lookupsCount = lookupsIndex?.keys?.count() ?: 0
  val lookupsAccumulator = mutableMapOf<String, Int>()
  if (lookupsCount > 0) {
    ByteArrayOutputStream().use { byteStream ->
      DataOutputStream(byteStream).use { dataOut ->
        dataOut.writeVersion()
        dataOut.writeInt(lookupsCount)
        lookupsIndex?.keys?.forEach { key ->
          val dependencies = lookupsIndex.getDependencies(key)
            .mapNotNull { (it as? JvmNodeReferenceID)?.nodeName }
          dataOut.writeUTF(key.toFqName())
          dataOut.writeInt(dependencies.count())
          dependencies.forEach {
            dataOut.writeInt(it.addFilePathIfNeeded(lookupsAccumulator)) }
        }
      }
      byteStream.toByteArray()
    }.also { serializedDataResult["lookups"] = it }

    if (lookupsAccumulator.isNotEmpty()) {
      ByteArrayOutputStream().use { byteStream ->
        DataOutputStream(byteStream).use { dataOut ->
          dataOut.writeVersion()
          dataOut.writeInt(lookupsAccumulator.size)
          lookupsAccumulator.forEach { (path, fileId) ->
            dataOut.writeInt(fileId)
            dataOut.writeUTF(path)
          }
        }
        byteStream.toByteArray()
      }.also { serializedDataResult["fileIdToPath"] = it }
    }
  }

  return serializedDataResult
}

/**
 * Converts the current `ReferenceID` to its fully qualified name (FqName) representation.
 *
 * Example transformation:
 * Input:  `com/intellij/AppKt$foo$3$1`
 * Output: `com.intellij.AppKt.foo`
 *
 * @return The fully qualified name as a `String`.
 */
private fun ReferenceID.toFqName(): String {
  return (this as? JvmNodeReferenceID)?.nodeName
           ?.replace('/', '.')
           ?.replace(DOLLAR_DIGITS_SUFFIX_REGEX, "")
           ?.replace('$', '.')
         ?: this.toString()
}

private val DOLLAR_DIGITS_SUFFIX_REGEX = Regex("(?:\\$\\d+)+$")

private fun String.addFilePathIfNeeded(fileIdToPathEntryAccumulator: MutableMap<String, Int>): Int {
  return fileIdToPathEntryAccumulator.computeIfAbsent(this) { fileIdToPathEntryAccumulator.size + 1 }
}

private fun DataOutputStream.writeVersion() = writeInt(VERSION)