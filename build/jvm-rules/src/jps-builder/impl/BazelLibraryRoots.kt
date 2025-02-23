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
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.state.notNullUtfStringFieldType
import org.jetbrains.bazel.jvm.jps.state.readArrowFile
import org.jetbrains.bazel.jvm.jps.state.writeVectorToFile
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.jps.incremental.storage.dataTypes.LibRootUpdateResult
import org.jetbrains.jps.incremental.storage.dataTypes.LibraryRoots
import java.nio.file.Files
import java.nio.file.Path

private val depFileField = Field("file", notNullUtfStringFieldType, null)
private val nsField = Field("namespace", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.notNullable(ArrowType.Binary.INSTANCE), null)

private val depDescriptorSchema = Schema(java.util.List.of(depFileField, nsField, digestField))

internal fun loadLibRootState(
  storageFile: Path,
  allocator: RootAllocator,
  relativizer: PathTypeAwareRelativizer,
  span: Span,
): MutableMap<Path, LibRootDescriptor> {
  readArrowFile(storageFile, allocator, span) { fileReader ->
    val root = fileReader.vectorSchemaRoot

    val depFileVector = root.getVector(depFileField) as VarCharVector
    val nsFileVector = root.getVector(nsField) as VarCharVector
    val digestVector = root.getVector(digestField) as VarBinaryVector

    val result = hashMap<Path, LibRootDescriptor>(root.rowCount)
    for (rowIndex in 0 until root.rowCount) {
      val file = relativizer.toAbsoluteFile(depFileVector.get(rowIndex).decodeToString(), RelativePathType.SOURCE)
      val ns = nsFileVector.get(rowIndex).decodeToString()
      val digest = digestVector.get(rowIndex)

      result.put(file, LibRootDescriptor(ns, digest))
    }
    return result
  }
  return hashMap<Path, LibRootDescriptor>()
}

internal class BazelLibraryRoots(
  private val dependencyFileToDigest: Map<Path, ByteArray>,
  private val storageFile: Path,
  private val roots: MutableMap<Path, LibRootDescriptor>,
) : LibraryRoots {
  private var isChanged = false

  @Synchronized
  override fun getRoots(acc: MutableSet<Path>): Set<Path> {
    acc.addAll(roots.keys)
    return acc
  }

  @Synchronized
  override fun updateIfExists(root: Path, namespace: String): LibRootUpdateResult {
    // known for JPS (as requested) but not found in an actual set of deps => deleted
    val currentDigest = dependencyFileToDigest.get(root) ?: return LibRootUpdateResult.DOES_NOT_EXIST

    // known for JPS (as requested), found in an actual set of deps, but not in a saved state => unknown (not updated and not deleted)
    val oldDescriptor = roots.get(root) ?: return LibRootUpdateResult.UNKNOWN
    // The namespace is irrelevant as it's used as the library name.
    // We always use the same library as the container for all dependencies.
    if (oldDescriptor.digest.contentEquals(currentDigest)) {
      return LibRootUpdateResult.UNKNOWN
    }

    roots.put(root, LibRootDescriptor(namespace = namespace, digest = currentDigest))
    isChanged = true
    return LibRootUpdateResult.EXISTS_AND_MODIFIED
  }

  @Synchronized
  override fun removeRoots(toRemove: Iterable<Path>) {
    for (deletedRoot in toRemove) {
      if (roots.remove(deletedRoot) != null) {
        isChanged = true
      }
    }
  }

  @Synchronized
  override fun getNamespace(root: Path): String? = roots.get(root)?.namespace

  @Synchronized
  fun saveState(allocator: RootAllocator, relativizer: PathTypeAwareRelativizer) {
    if (!isChanged) {
      return
    }

    if (roots.isEmpty()) {
      Files.deleteIfExists(storageFile)
    }
    else {
      VectorSchemaRoot.create(depDescriptorSchema, allocator).use { root ->
        val sortedKeys = roots.keys.sorted()

        val depFileVector = root.getVector(depFileField) as VarCharVector
        val nsFileVector = root.getVector(nsField) as VarCharVector
        val digestVector = root.getVector(digestField) as VarBinaryVector

        depFileVector.setInitialCapacity(sortedKeys.size, 100.0)
        depFileVector.allocateNew(sortedKeys.size)

        digestVector.setInitialCapacity(sortedKeys.size, 56.0)
        digestVector.allocateNew(sortedKeys.size)
        var rowIndex = 0
        for (key in sortedKeys) {
          val descriptor = roots.get(key)!!

          depFileVector.setSafe(rowIndex, relativizer.toRelative(key, RelativePathType.SOURCE).toByteArray())
          nsFileVector.setSafe(rowIndex, descriptor.namespace.toByteArray())
          digestVector.setSafe(rowIndex, descriptor.digest)

          rowIndex++
        }
        root.setRowCount(rowIndex)
        writeVectorToFile(file = storageFile, root = root, metadata = emptyMap())
      }
    }
    isChanged = false
  }
}

internal data class LibRootDescriptor(
  @JvmField val namespace: String,
  @JvmField val digest: ByteArray,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is LibRootDescriptor) return false

    if (namespace != other.namespace) return false
    if (!digest.contentEquals(other.digest)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = namespace.hashCode()
    result = 31 * result + digest.contentHashCode()
    return result
  }
}