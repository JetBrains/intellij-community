// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package org.jetbrains.bazel.jvm.worker.state

import androidx.collection.MutableObjectList
import androidx.collection.ObjectList
import androidx.collection.ScatterMap
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
import org.jetbrains.bazel.jvm.util.toLinkedSet
import java.nio.file.Files
import java.nio.file.Path

private val depFileField = Field("file", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.notNullable(ArrowType.Binary.INSTANCE), null)

private val depDescriptorSchema = Schema(java.util.List.of(depFileField, digestField))

data class DependencyStateResult(
  @JvmField val dependencies: ObjectList<DependencyDescriptor>,
  @JvmField val isSaveNeeded: Boolean,
)

// actually, ADDED or DELETED not possible - in configureClasspath we compute DEPENDENCY_PATH_LIST,
// so, if a dependency list is changed, then we perform rebuild
enum class DependencyState {
  CHANGED, UNCHANGED, ADDED, DELETED
}

fun isDependencyTracked(file: Path): Boolean = isDependencyTracked(file.toString())

fun isDependencyTracked(path: String): Boolean = path.endsWith(".abi.jar")

// do not include state into equals/hashCode - DependencyDescriptor is used as a key for Caffeine cache
data class DependencyDescriptor(
  @JvmField val file: Path,
  // we use DependencyDescriptor as a key for cache of diff between old and new versions, so, we must include oldDigest into equals/hashCode
  @JvmField val oldDigest: ByteArray?,
  @JvmField val digest: ByteArray?,
  @JvmField val state: DependencyState,
) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is DependencyDescriptor) return false

    if (file != other.file) return false
    if (!digest.contentEquals(other.digest)) return false

    return true
  }

  override fun hashCode(): Int {
    var result = file.hashCode()
    result = 31 * result + digest.contentHashCode()
    result = 31 * result + oldDigest.contentHashCode()
    return result
  }
}

// please note - in configureClasspath we compute UNTRACKED_DEPENDENCY_DIGEST_LIST, meaning that some files in classpath are untracked,
// and any change of untracked dependency leads to rebuild (this method will not be called)
fun loadDependencyState(
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
  storageFile: Path,
  allocator: RootAllocator,
  relativizer: PathRelativizer,
  trackableDependencyFiles: ObjectList<Path>,
  span: Span,
): DependencyStateResult {
  readArrowFile(storageFile, allocator, span) { fileReader ->
    val newFiles = trackableDependencyFiles.toLinkedSet()
    val root = fileReader.vectorSchemaRoot

    val depFileVector = root.getVector(depFileField) as VarCharVector
    val digestVector = root.getVector(digestField) as VarBinaryVector

    val result = MutableObjectList<DependencyDescriptor>(root.rowCount)
    var isSaveNeeded = false
    for (rowIndex in 0 until root.rowCount) {
      val path = depFileVector.get(rowIndex).decodeToString()
      if (!isDependencyTracked(path)) {
        continue
      }

      val file = relativizer.toAbsoluteFile(path)

      val oldDigest = digestVector.get(rowIndex)
      val currentDigest = dependencyFileToDigest.get(file)
      val state = if (currentDigest == null) {
        isSaveNeeded = true
        DependencyState.DELETED
      }
      else {
        newFiles.remove(file)
        if (currentDigest.contentEquals(oldDigest)) {
          DependencyState.UNCHANGED
        }
        else {
          isSaveNeeded = true
          DependencyState.CHANGED
        }
      }
      result.add(DependencyDescriptor(
        file = file,
        digest = currentDigest,
        oldDigest = oldDigest,
        state = state,
      ))
    }

    if (!newFiles.isEmpty()) {
      isSaveNeeded = true
      for (file in newFiles) {
        result.add(newDependency(file, dependencyFileToDigest))
      }
    }
    DependencyStateResult(result, isSaveNeeded = isSaveNeeded)
  }?.let { return it }

  return createNewDependencyList(trackableDependencyFiles, dependencyFileToDigest)
}

fun createNewDependencyList(
  trackableDependencyFiles: ObjectList<Path>,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>
): DependencyStateResult {
  val result = MutableObjectList<DependencyDescriptor>(trackableDependencyFiles.size)
  trackableDependencyFiles.forEach { file ->
    result.add(newDependency(file, dependencyFileToDigest))
  }
  return DependencyStateResult(result, isSaveNeeded = true)
}

private fun newDependency(
  file: Path,
  dependencyFileToDigest: ScatterMap<Path, ByteArray>,
): DependencyDescriptor {
  return DependencyDescriptor(
    file = file,
    digest = requireNotNull(dependencyFileToDigest.get(file)) { "cannot find actual digest for $file" },
    oldDigest = null,
    state = DependencyState.ADDED,
  )
}

class DependencyStateStorage(
  private val storageFile: Path,
  @JvmField val state: DependencyStateResult,
) {
  private var isChanged = false

  suspend fun saveState(allocator: RootAllocator, relativizer: PathRelativizer) {
    if (!state.isSaveNeeded) {
      return
    }

    if (state.dependencies.isEmpty()) {
      Files.deleteIfExists(storageFile)
      isChanged = false
      return
    }

    val sorted = Array(state.dependencies.size) { state.dependencies[it] }
    sorted.sortBy { it.file }

    VectorSchemaRoot.create(depDescriptorSchema, allocator).use { root ->
      val depFileVector = root.getVector(depFileField) as VarCharVector
      val digestVector = root.getVector(digestField) as VarBinaryVector

      depFileVector.setInitialCapacity(sorted.size, 100.0)
      depFileVector.allocateNew(sorted.size)

      digestVector.setInitialCapacity(sorted.size, 56.0)
      digestVector.allocateNew(sorted.size)
      var rowIndex = 0
      for (descriptor in sorted) {
        if (descriptor.state == DependencyState.DELETED) {
          // for now, we perform a full rebuild if a dependency chain is changed (removed or added)
          throw IllegalStateException("saving deleted dependency not yet supported")
        }
        depFileVector.setSafe(rowIndex, relativizer.toRelative(descriptor.file).toByteArray())
        digestVector.setSafe(rowIndex, descriptor.digest)

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