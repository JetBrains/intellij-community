// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.state

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.BitVector
import org.apache.arrow.vector.VarBinaryVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.complex.ListVector
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.apache.arrow.vector.types.pojo.Schema
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.emptyList
import org.jetbrains.bazel.jvm.hashMap
import org.jetbrains.bazel.jvm.jps.SourceDescriptor
import org.jetbrains.bazel.jvm.jps.emptyStringArray
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import java.io.IOException
import java.nio.file.Path

// https://observablehq.com/@huggingface/apache-arrow-quick-view

private val sourceFileField = Field("sourceFile", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.notNullable(ArrowType.Binary.INSTANCE), null)
private val isChangedField = Field("changed", FieldType.notNullable(ArrowType.Bool.INSTANCE), null)
private val outputListField = Field(
  "outputs",
  FieldType.notNullable(ArrowType.List()),
  java.util.List.of(Field("output", notNullUtfStringFieldType, null))
)

private val sourceDescriptorSchema = Schema(java.util.List.of(sourceFileField, digestField, isChangedField, outputListField))

// returns a reason to force rebuild
private fun checkConfiguration(
  metadata: Map<String, String>,
  targetDigests: TargetConfigurationDigestContainer,
): String? {
  for (kind in TargetConfigurationDigestProperty.entries) {
    val storedHashAsString = metadata.get(kind.name)?.let { metadata.get(kind.name) }
    val actualHash = targetDigests.get(kind)
    if (actualHash != java.lang.Long.parseUnsignedLong(storedHashAsString, Character.MAX_RADIX)) {
      val expectedAsString = java.lang.Long.toUnsignedString(actualHash, Character.MAX_RADIX)
      return "configuration digest mismatch (${kind.description}): expected $expectedAsString, got $storedHashAsString"
    }
  }
  return null
}

@VisibleForTesting
fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathTypeAwareRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: Map<Path, ByteArray>,
): LoadStateResult? {
  return loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = relativizer,
    allocator = allocator,
    sourceFileToDigest = sourceFileToDigest,
    targetDigests = null,
    parentSpan = null,
  )
}

internal fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathTypeAwareRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: Map<Path, ByteArray>,
  targetDigests: TargetConfigurationDigestContainer?,
  parentSpan: Span?,
): LoadStateResult? {
  readArrowFile(buildStateFile, allocator, parentSpan) { fileReader ->
    if (targetDigests != null) {
      val rebuildRequested = checkConfiguration(metadata = fileReader.metaData, targetDigests = targetDigests)
      if (rebuildRequested != null) {
        if (parentSpan == null) {
          throw IOException(rebuildRequested)
        }
        else {
          return LoadStateResult(
            rebuildRequested = rebuildRequested,
            map = createInitialSourceMap(sourceFileToDigest),
            changedFiles = emptyList(),
            deletedFiles = emptyList(),
          )
        }
      }
    }

    return doLoad(
      root = fileReader.vectorSchemaRoot,
      actualDigestMap = sourceFileToDigest,
      relativizer = relativizer,
      parentSpan = parentSpan,
    )
  }

  return null
}

internal fun createInitialSourceMap(actualDigestMap: Map<Path, ByteArray>): Map<Path, SourceDescriptor> {
  val result = hashMap<Path, SourceDescriptor>(actualDigestMap.size)
  for ((path, digest) in actualDigestMap) {
    result.put(path, SourceDescriptor(sourceFile = path, digest = digest, outputs = emptyStringArray, isChanged = true))
  }
  return result
}

fun saveBuildState(
  buildStateFile: Path,
  list: Array<SourceDescriptor>,
  relativizer: PathTypeAwareRelativizer,
  metadata: Map<String, String>,
  allocator: RootAllocator,
) {
  VectorSchemaRoot.create(sourceDescriptorSchema, allocator).use { root ->
    val sourceFileVector = root.getVector(sourceFileField) as VarCharVector
    val digestVector = root.getVector(digestField) as VarBinaryVector
    val isChangedField = root.getVector(isChangedField) as BitVector
    val outputListVector = root.getVector(outputListField) as ListVector

    sourceFileVector.setInitialCapacity(list.size, 100.0)
    sourceFileVector.allocateNew(list.size)

    digestVector.setInitialCapacity(list.size, 56.0)
    digestVector.allocateNew(list.size)
    isChangedField.allocateNew(list.size)

    outputListVector.setInitialCapacity(list.size, 10.0)
    outputListVector.allocateNew()

    val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector
    var rowIndex = 0
    var outputRowIndex = 0
    for (descriptor in list) {
      sourceFileVector.setSafe(rowIndex, relativizer.toRelative(descriptor.sourceFile, RelativePathType.SOURCE).toByteArray())
      digestVector.setSafe(rowIndex, descriptor.digest)
      isChangedField.setSafe(rowIndex, if (descriptor.isChanged) 1 else 0)

      val outputs = descriptor.outputs
      outputListVector.startNewValue(rowIndex)
      if (outputs.isEmpty()) {
        outputListVector.endValue(rowIndex, 0)
      }
      else {
        for (output in outputs) {
          outputListInnerVector.setSafe(outputRowIndex++, output.toByteArray())
        }
        outputListVector.endValue(rowIndex, outputs.size)
      }

      rowIndex++
    }

    root.setRowCount(rowIndex)

    writeVectorToFile(buildStateFile, root, metadata)
  }
}

data class LoadStateResult(
  @JvmField val rebuildRequested: String?,

  @JvmField val map: Map<Path, SourceDescriptor>,
  @JvmField val changedFiles: List<Path>,
  @JvmField val deletedFiles: List<RemovedFileInfo>,
)

class RemovedFileInfo(
  @JvmField val sourceFile: Path,
  @JvmField val outputs: Array<String>,
)

@OptIn(ExperimentalStdlibApi::class)
private fun doLoad(
  root: VectorSchemaRoot,
  actualDigestMap: Map<Path, ByteArray>,
  relativizer: PathTypeAwareRelativizer,
  parentSpan: Span?,
): LoadStateResult {
  val sourceFileVector = root.getVector(sourceFileField) as VarCharVector
  val digestVector = root.getVector(digestField) as VarBinaryVector
  val isChangedVector = root.getVector(isChangedField) as BitVector
  val outputListVector = root.getVector(outputListField) as ListVector
  val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector

  // init a new state
  val result = hashMap<Path, SourceDescriptor>(actualDigestMap.size)
  val newFiles = hashMap(actualDigestMap)
  var outputIndex = 0
  val changedFiles = ArrayList<Path>()
  val deletedFiles = ArrayList<RemovedFileInfo>()
  for (rowIndex in 0 until root.rowCount) {
    val sourceFile = relativizer.toAbsoluteFile(sourceFileVector.get(rowIndex).decodeToString(), RelativePathType.SOURCE)
    val digest = digestVector.get(rowIndex)

    val start = outputListVector.getElementStartIndex(rowIndex)
    val end = outputListVector.getElementEndIndex(rowIndex)
    val size = end - start
    val outputs = if (size == 0) null else Array(size) {
      String(outputListInnerVector.get(it + start))
    }

    // check the status of the stored source file
    val actualDigest = newFiles.remove(sourceFile)
    if (actualDigest == null) {
      // removed
      if (outputs != null) {
        deletedFiles.add(RemovedFileInfo(sourceFile = sourceFile, outputs = outputs))
      }
    }
    else {
      val flag = isChangedVector.get(rowIndex) == 1
      val isChanged = flag || !digest.contentEquals(actualDigest)
      if (parentSpan != null && isChanged) {
        if (flag) {
          parentSpan.addEvent("file is changed (flag)", Attributes.of(
            AttributeKey.stringKey("sourceFile"), sourceFile.toString(),
          ))
        }
        else {
          parentSpan.addEvent("file is changed (digest)", Attributes.of(
            AttributeKey.stringKey("sourceFile"), sourceFile.toString(),
            AttributeKey.stringKey("oldDigest"), digest.toHexString(),
            AttributeKey.stringKey("newDigest"), actualDigest.toHexString(),
          ))
        }
      }
      result.put(sourceFile, SourceDescriptor(
        sourceFile = sourceFile,
        digest = actualDigest,
        outputs = outputs ?: emptyStringArray,
        isChanged = isChanged,
      ))

      if (isChanged) {
        changedFiles.add(sourceFile)
      }
    }

    outputIndex++
  }

  // if a file was not removed from newFiles, it means that file is unknown and so, a new one
  for (entry in newFiles) {
    // for now, missing digest means that file is changed (not compiled)
    result.put(entry.key, SourceDescriptor(sourceFile = entry.key, digest = entry.value, outputs = emptyStringArray, isChanged = true))
  }

  return LoadStateResult(map = result, changedFiles = changedFiles, deletedFiles = deletedFiles, rebuildRequested = null)
}