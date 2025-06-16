// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.worker.state

import androidx.collection.MutableScatterMap
import androidx.collection.ScatterMap
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import org.jetbrains.bazel.jvm.util.emptyStringArray
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

fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathRelativizer,
  allocator: RootAllocator,
  sourceFileToDigest: ScatterMap<Path, ByteArray>,
  parentSpan: Span?,
): SourceFileStateResult? {
  return readArrowFile(buildStateFile, allocator, parentSpan) { fileReader ->
    doLoad(
      root = fileReader.vectorSchemaRoot,
      actualDigestMap = sourceFileToDigest,
      relativizer = relativizer,
      parentSpan = parentSpan,
    )
  }
}

fun createInitialSourceMap(actualDigestMap: ScatterMap<Path, ByteArray>): ScatterMap<Path, SourceDescriptor> {
  val result = MutableScatterMap<Path, SourceDescriptor>(actualDigestMap.size)
  actualDigestMap.forEach { path, digest ->
    result.put(path, SourceDescriptor(sourceFile = path, digest = digest, outputs = emptyStringArray, isChanged = true))
  }
  return result
}

suspend fun saveBuildState(
  buildStateFile: Path,
  list: Array<SourceDescriptor>,
  relativizer: PathRelativizer,
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
      sourceFileVector.setSafe(rowIndex, relativizer.toRelative(descriptor.sourceFile).toByteArray())
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

    withContext(Dispatchers.IO) {
      writeVectorToFile(buildStateFile, root)
    }
  }
}

data class SourceFileStateResult(
  @JvmField val map: ScatterMap<Path, SourceDescriptor>,
  @JvmField val changedOrAddedFiles: List<Path>,
  @JvmField val deletedFiles: List<RemovedFileInfo>,
)

class RemovedFileInfo(
  @JvmField val sourceFile: Path,
  @JvmField val outputs: Array<String>,
)

@OptIn(ExperimentalStdlibApi::class)
private fun doLoad(
  root: VectorSchemaRoot,
  actualDigestMap: ScatterMap<Path, ByteArray>,
  relativizer: PathRelativizer,
  parentSpan: Span?,
): SourceFileStateResult {
  val sourceFileVector = root.getVector(sourceFileField) as VarCharVector
  val digestVector = root.getVector(digestField) as VarBinaryVector
  val isChangedVector = root.getVector(isChangedField) as BitVector
  val outputListVector = root.getVector(outputListField) as ListVector
  val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector

  // init a new state
  val result = MutableScatterMap<Path, SourceDescriptor>(actualDigestMap.size)
  val newFiles = MutableScatterMap<Path, ByteArray>(actualDigestMap.size)
  newFiles.putAll(actualDigestMap)
  var outputIndex = 0
  val changedFiles = ArrayList<Path>()
  val deletedFiles = ArrayList<RemovedFileInfo>()
  for (rowIndex in 0 until root.rowCount) {
    val sourceFile = relativizer.toAbsoluteFile(sourceFileVector.get(rowIndex).decodeToString())
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

  // if a file was not removed from newFiles, it means that the file is unknown and so, a new one
  if (newFiles.isNotEmpty()) {
    newFiles.forEach { path, digest ->
      // for now, missing digest means that the file is changed (not compiled)
      result.put(path, SourceDescriptor(sourceFile = path, digest = digest, outputs = emptyStringArray, isChanged = true))
    }
    newFiles.forEachKey {
      changedFiles.add(it)
    }
  }

  return SourceFileStateResult(map = result, changedOrAddedFiles = changedFiles, deletedFiles = deletedFiles)
}