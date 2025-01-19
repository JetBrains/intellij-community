// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.state

import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.FixedSizeBinaryVector
import org.apache.arrow.vector.VarCharVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.complex.ListVector
import org.apache.arrow.vector.ipc.ArrowFileReader
import org.apache.arrow.vector.ipc.ArrowFileWriter
import org.apache.arrow.vector.types.pojo.ArrowType
import org.apache.arrow.vector.types.pojo.Field
import org.apache.arrow.vector.types.pojo.FieldType
import org.apache.arrow.vector.types.pojo.Schema
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.bazel.jvm.jps.RequestLog
import org.jetbrains.bazel.jvm.jps.SourceDescriptor
import org.jetbrains.bazel.jvm.jps.emptyList
import org.jetbrains.bazel.jvm.jps.hashMap
import org.jetbrains.intellij.build.io.writeFileUsingTempFile
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.kotlin.utils.addToStdlib.enumSetOf
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.collections.iterator

// https://observablehq.com/@huggingface/apache-arrow-quick-view

internal const val STATE_FILE_FORMAT_VERSION = 1.toString()
internal const val VERSION_META_NAME = "version"

private val WRITE_FILE_OPTION = enumSetOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
private val READ_FILE_OPTION = enumSetOf(StandardOpenOption.READ)

private val notNullUtfStringFieldType = FieldType.notNullable(ArrowType.Utf8())

private val sourceFileField = Field("sourceFile", notNullUtfStringFieldType, null)
private val digestField = Field("digest", FieldType.nullable(ArrowType.FixedSizeBinary(32)), null)
private val outputListField = Field(
  "outputs",
  FieldType.notNullable(ArrowType.List()),
  java.util.List.of(Field("output", notNullUtfStringFieldType, null))
)

private val sourceDescriptorSchema = Schema(java.util.List.of(sourceFileField, digestField, outputListField))

// returns a reason to force rebuild
private fun checkConfiguration(
  metadata: Map<String, String>,
  targetDigests: TargetConfigurationDigestContainer,
): String? {
  val formatVersion = metadata.get(VERSION_META_NAME)
  if (formatVersion != STATE_FILE_FORMAT_VERSION) {
    return "format version mismatch: expected $STATE_FILE_FORMAT_VERSION, actual $formatVersion"
  }

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
  actualDigestMap: Map<Path, ByteArray>,
): LoadStateResult? {
  return loadBuildState(
    buildStateFile = buildStateFile,
    relativizer = relativizer,
    allocator = allocator,
    actualDigestMap = actualDigestMap,
    log = null,
    targetDigests = null,
  )
}

internal fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathTypeAwareRelativizer,
  allocator: RootAllocator,
  log: RequestLog?,
  actualDigestMap: Map<Path, ByteArray>,
  targetDigests: TargetConfigurationDigestContainer?,
): LoadStateResult? {
  try {
    FileChannel.open(buildStateFile, READ_FILE_OPTION).use { fileChannel ->
      ArrowFileReader(fileChannel, allocator).use { fileReader ->
        // metadata is available only after loading batch
        fileReader.loadNextBatch()

        if (targetDigests != null) {
          val rebuildRequested = checkConfiguration(metadata = fileReader.metaData, targetDigests = targetDigests)
          if (rebuildRequested != null) {
            if (log == null) {
              throw IOException(rebuildRequested)
            }
            else {
              return LoadStateResult(
                rebuildRequested = rebuildRequested,
                map = createInitialSourceMap(actualDigestMap),
                changedFiles = emptyList(),
                deletedFiles = emptyList(),
              )
            }
          }
        }

        return doLoad(
          root = fileReader.vectorSchemaRoot,
          actualDigestMap = actualDigestMap,
          relativizer = relativizer,
        )
      }
    }
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: Throwable) {
    if (log == null) {
      throw e
    }

    log.error("cannot load $buildStateFile", e)
    // will be deleted by caller
    return null
  }
}

internal fun createInitialSourceMap(actualDigestMap: Map<Path, ByteArray>): Map<Path, SourceDescriptor> {
  val result = hashMap<Path, SourceDescriptor>(actualDigestMap.size)
  for ((path, digest) in actualDigestMap) {
    result.put(path, SourceDescriptor(sourceFile = path, digest = digest, outputs = null))
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
    val sourceFileVector = root.getVector("sourceFile") as VarCharVector
    val digestVector = root.getVector("digest") as FixedSizeBinaryVector
    val outputListVector = root.getVector("outputs") as ListVector

    sourceFileVector.setInitialCapacity(list.size, 100.0)
    sourceFileVector.allocateNew(list.size)

    digestVector.allocateNew(list.size)

    outputListVector.setInitialCapacity(list.size, 10.0)
    outputListVector.allocateNew()

    val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector
    var rowIndex = 0
    var outputRowIndex = 0
    for (descriptor in list) {
      sourceFileVector.setSafe(rowIndex, relativizer.toRelative(descriptor.sourceFile, RelativePathType.SOURCE).toByteArray())
      if (descriptor.digest == null) {
        digestVector.setNull(rowIndex)
      }
      else {
        digestVector.setSafe(rowIndex, descriptor.digest!!)
      }

      val outputs = descriptor.outputs
      outputListVector.startNewValue(rowIndex)
      if (outputs == null) {
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

    writeFileUsingTempFile(buildStateFile) { tempFile ->
      FileChannel.open(tempFile, WRITE_FILE_OPTION).use { fileChannel ->
        ArrowFileWriter(root, null, fileChannel, metadata).use { fileWriter ->
          fileWriter.start()
          fileWriter.writeBatch()
          fileWriter.end()
        }
      }
    }
  }
}

// do not use an open-addressing hash map or immutable map - see https://stackoverflow.com/a/16303438
data class LoadStateResult(
  @JvmField val rebuildRequested: String?,

  @JvmField val map: Map<Path, SourceDescriptor>,
  @JvmField val changedFiles: List<Path>,
  @JvmField val deletedFiles: List<RemovedFileInfo>,
)

class RemovedFileInfo(
  @JvmField val sourceFile: Path,
  @JvmField val outputs: List<Path>,
)

private fun doLoad(
  root: VectorSchemaRoot,
  actualDigestMap: Map<Path, ByteArray>,
  relativizer: PathTypeAwareRelativizer,
): LoadStateResult {
  val sourceFileVector = root.getVector(sourceFileField) as VarCharVector
  val digestVector = root.getVector(digestField) as FixedSizeBinaryVector
  val outputListVector = root.getVector(outputListField) as ListVector
  val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector

  // init a new state
  val result = hashMap<Path, SourceDescriptor>(actualDigestMap.size)
  val newFiles = hashMap(actualDigestMap)
  var outputIndex = 0
  val changedFiles = ArrayList<Path>()
  val deletedFiles = ArrayList<RemovedFileInfo>()
  for (rowIndex in 0 until root.rowCount) {
    val sourceFile = relativizer.toAbsoluteFile(String(sourceFileVector.get(rowIndex)), RelativePathType.SOURCE)
    val digest = if (digestVector.isNull(rowIndex)) null else digestVector.get(rowIndex)
    val outputs: Array<String>?
    if (outputListVector.isNull(rowIndex)) {
      outputs = null
    }
    else {
      val start = outputListVector.getElementStartIndex(rowIndex)
      val end = outputListVector.getElementEndIndex(rowIndex)
      val size = end - start
      outputs = if (size == 0) null else Array<String>(size) {
        String(outputListInnerVector.get(it + start))
      }
    }

    // check the status of the stored source file
    val actualDigest = newFiles.remove(sourceFile)
    if (actualDigest == null) {
      // removed
      if (outputs != null) {
        deletedFiles.add(RemovedFileInfo(
          sourceFile = sourceFile,
          outputs = outputs.map { relativizer.toAbsoluteFile(it, RelativePathType.OUTPUT) },
        ))
      }
    }
    else {
      val changed = !digest.contentEquals(actualDigest)
      result.put(sourceFile, SourceDescriptor(sourceFile = sourceFile, digest = digest.takeIf { !changed }, outputs = outputs?.asList()))
    }

    outputIndex++
  }

  // if a file was not removed from newFiles, it means that file is unknown and so, a new one
  for (entry in newFiles) {
    // for now, missing digest means that file is changed (not compiled)
    result.put(entry.key, SourceDescriptor(sourceFile = entry.key, digest = null, outputs = null))
  }

  return LoadStateResult(map = result, changedFiles = changedFiles, deletedFiles = deletedFiles, rebuildRequested = null)
}