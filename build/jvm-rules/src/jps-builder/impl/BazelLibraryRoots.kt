// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.jps.impl

import io.opentelemetry.api.trace.Span
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.VarBinaryVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.apache.arrow.vector.types.pojo.Schema
import org.jetbrains.bazel.jvm.jps.state.notNullUtfStringFieldType
import org.jetbrains.bazel.jvm.jps.state.readArrowFile
import org.jetbrains.bazel.jvm.jps.state.writeVectorToFile
import org.jetbrains.bazel.jvm.linkedSet
import org.jetbrains.jps.incremental.dependencies.BAZEl_LIB_CONTAINER_NS
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.incremental.storage.dataTypes.LibRootUpdateResult
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots
import java.nio.file.Files
import java.nio.file.Path

private val depFileField = Field("file", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.notNullable(ArrowType.Binary.INSTANCE), null)

private val depDescriptorSchema = Schema(java.util.List.of(depFileField, digestField))

internal fun loadLibRootState(
  storageFile: Path,
  allocator: RootAllocator,
  relativizer: PathTypeAwareRelativizer,
  span: Span,
): MutableMap<Path, ByteArray> {
  readArrowFile(storageFile, allocator, span) { fileReader ->
    val root = fileReader.vectorSchemaRoot

    val depFileVector = root.getVector(depFileField) as VarCharVector
    val digestVector = root.getVector(digestField) as VarBinaryVector

    val result = LinkedHashMap<Path, ByteArray>(root.rowCount)
    for (rowIndex in 0 until root.rowCount) {
      val file = relativizer.toAbsoluteFile(depFileVector.get(rowIndex).decodeToString(), RelativePathType.SOURCE)
      val digest = digestVector.get(rowIndex)

      result.put(file, digest)
    }
    return result
  }
  return LinkedHashMap()
}

internal class BazelLibraryRoots(
  private val actualDependencyFileToDigest: Map<Path, ByteArray>,
  private val storageFile: Path,
  private val fileToDigest: MutableMap<Path, ByteArray>,
) : LibraryRoots {
  private var isChanged = false

  @Synchronized
  override fun getRoots(acc: MutableSet<Path>): Set<Path> {
    acc.addAll(fileToDigest.keys)
    return acc
  }

  @Synchronized
  override fun updateIfExists(root: Path, namespace: String): LibRootUpdateResult {
    throw UnsupportedOperationException("updateIfExists is not supported for BazelLibraryRoots")
  }

  @Synchronized
  fun checkState(updated: MutableSet<Path>, classpath: Array<Path>): Set<Path> {
    val deleted = linkedSet(fileToDigest.keys)
    for (file in classpath) {
      val isKnown = deleted.remove(file)

      val currentDigest = actualDependencyFileToDigest.get(file)!!

      val oldDigest = if (isKnown) fileToDigest.get(file) else null
      if (oldDigest == null) {
        // added?
        fileToDigest.put(file, currentDigest)
        isChanged = true
        continue
      }

      if (oldDigest.contentEquals(currentDigest)) {
        // unchanged
        continue
      }

      // changed
      fileToDigest.put(file, currentDigest)
      isChanged = true
      updated.add(file)
    }
    if (deleted.isNotEmpty()) {
      fileToDigest.keys.removeAll(deleted)
    }
    return deleted
  }

  @Synchronized
  override fun removeRoots(toRemove: Iterable<Path>) {
    for (deletedRoot in toRemove) {
      if (fileToDigest.remove(deletedRoot) != null) {
        isChanged = true
      }
    }
  }

  @Synchronized
  override fun getNamespace(root: Path): String? = BAZEl_LIB_CONTAINER_NS

  @Synchronized
  fun saveState(allocator: RootAllocator, relativizer: PathTypeAwareRelativizer) {
    if (!isChanged) {
      return
    }

    if (fileToDigest.isEmpty()) {
      Files.deleteIfExists(storageFile)
    }
    else {
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

          depFileVector.setSafe(rowIndex, relativizer.toRelative(key, RelativePathType.SOURCE).toByteArray())
          digestVector.setSafe(rowIndex, digest)

          rowIndex++
        }
        root.setRowCount(rowIndex)
        writeVectorToFile(file = storageFile, root = root, metadata = emptyMap())
      }
    }
    isChanged = false
  }
}