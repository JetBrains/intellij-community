// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.state

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.VarBinaryVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.apache.arrow.vector.types.pojo.Schema
import org.jetbrains.bazel.jvm.hashMap
import java.nio.file.Files
import java.nio.file.Path

private val depFileField = Field("file", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.notNullable(ArrowType.Binary.INSTANCE), null)

private val depDescriptorSchema = Schema(java.util.List.of(depFileField, digestField))

data class DependencyStateResult(
  @JvmField val map: MutableMap<Path, ByteArray>,
)

fun loadDependencyState(
  storageFile: Path,
  allocator: RootAllocator,
  relativizer: PathRelativizer,
  span: Span,
): MutableMap<Path, ByteArray>? {
  readArrowFile(storageFile, allocator, span) { fileReader ->
    val root = fileReader.vectorSchemaRoot

    val depFileVector = root.getVector(depFileField) as VarCharVector
    val digestVector = root.getVector(digestField) as VarBinaryVector

    val result = hashMap<Path, ByteArray>(root.rowCount)
    for (rowIndex in 0 until root.rowCount) {
      val file = relativizer.toAbsoluteFile(depFileVector.get(rowIndex).decodeToString())
      val digest = digestVector.get(rowIndex)
      result.put(file, digest)
    }
    return result
  }
  return null
}

class DependencyStateStorage(
  private val actualDependencyFileToDigest: Map<Path, ByteArray>,
  private val storageFile: Path,
  private val classpath: Array<Path>,
  private val fileToDigest: MutableMap<Path, ByteArray>,
) {
  private var isChanged = false

  fun checkState(): List<Path> {
    val changedOrAdded = ArrayList<Path>()
    for (file in classpath) {
      val currentDigest = requireNotNull(actualDependencyFileToDigest.get(file)) { "cannot find actual digest for $file" }

      val oldDigest = fileToDigest.get(file)
      if (oldDigest == null || !oldDigest.contentEquals(currentDigest)) {
        fileToDigest.put(file, currentDigest)
        isChanged = true
        changedOrAdded.add(file)
      }
    }
    return changedOrAdded
  }

  suspend fun saveState(allocator: RootAllocator, relativizer: PathRelativizer) {
    if (!isChanged) {
      return
    }

    if (fileToDigest.isEmpty()) {
      Files.deleteIfExists(storageFile)
      isChanged = false
      return
    }

    VectorSchemaRoot.create(depDescriptorSchema, allocator).use { root ->
      val sortedKeys = fileToDigest.keys.sorted()

      val depFileVector = root.getVector(depFileField) as VarCharVector
      val digestVector = root.getVector(digestField) as VarBinaryVector

      depFileVector.setInitialCapacity(sortedKeys.size, 100.0)
      depFileVector.allocateNew(sortedKeys.size)

      digestVector.setInitialCapacity(sortedKeys.size, 56.0)
      digestVector.allocateNew(sortedKeys.size)
      var rowIndex = 0
      for (key in sortedKeys) {
        val digest = fileToDigest.get(key)!!

        depFileVector.setSafe(rowIndex, relativizer.toRelative(key).toByteArray())
        digestVector.setSafe(rowIndex, digest)

        rowIndex++
      }
      root.setRowCount(rowIndex)
      withContext(Dispatchers.IO) {
        writeVectorToFile(file = storageFile, root = root, metadata = emptyMap())
      }
    }
    isChanged = false
  }
}