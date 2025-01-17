@file:Suppress("UnstableApiUsage", "ReplaceJavaStaticMethodWithKotlinAnalog", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps

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
import org.jetbrains.intellij.build.io.writeFileUsingTempFile
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.kotlin.utils.addToStdlib.enumSetOf
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption

private const val STATE_FILE_FORMAT_VERSION = 1.toString()
private const val VERSION_META_NAME = "version"

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

private val stateFileMetadata = java.util.Map.of(VERSION_META_NAME, STATE_FILE_FORMAT_VERSION)

@VisibleForTesting
fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathTypeAwareRelativizer,
  allocator: RootAllocator,
  log: RequestLog?,
): HashMap<Path, SourceDescriptor>? {
  try {
    FileChannel.open(buildStateFile, READ_FILE_OPTION).use { fileChannel ->
      VectorSchemaRoot.create(sourceDescriptorSchema, allocator).use { root ->
        ArrowFileReader(fileChannel, allocator).use { fileReader ->
          // metadata is available only after loading batch
          fileReader.loadNextBatch()

          val formatVersion = fileReader.metaData.get(VERSION_META_NAME)
          if (formatVersion != STATE_FILE_FORMAT_VERSION) {
            val message = "format version mismatch: expected $STATE_FILE_FORMAT_VERSION, actual $formatVersion"
            if (log == null) {
              throw IOException(message)
            }
            else {
              log.warn(message)
            }
            return null
          }

          return doLoad(fileReader.vectorSchemaRoot, relativizer)
        }
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

@VisibleForTesting
fun saveBuildState(buildStateFile: Path, list: Array<SourceDescriptor>, relativizer: PathTypeAwareRelativizer, allocator: RootAllocator) {
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
        ArrowFileWriter(root, null, fileChannel, stateFileMetadata).use { fileWriter ->
          fileWriter.start()
          fileWriter.writeBatch()
          fileWriter.end()
        }
      }
    }
  }
}

private fun doLoad(root: VectorSchemaRoot, relativizer: PathTypeAwareRelativizer): HashMap<Path, SourceDescriptor> {
  val sourceFileVector = root.getVector(sourceFileField) as VarCharVector
  val digestVector = root.getVector(digestField) as FixedSizeBinaryVector
  val outputListVector = root.getVector(outputListField) as ListVector
  val outputListInnerVector = outputListVector.addOrGetVector<VarCharVector>(notNullUtfStringFieldType).vector

  val result = HashMap<Path, SourceDescriptor>(root.rowCount)
  var outputIndex = 0
  for (rowIndex in 0 until root.rowCount) {
    val sourceFile = relativizer.toAbsoluteFile(String(sourceFileVector.get(rowIndex)), RelativePathType.SOURCE)
    val digest = if (digestVector.isNull(rowIndex)) null else digestVector.get(rowIndex)
    val outputs = if (outputListVector.isNull(rowIndex)) {
      null
    }
    else {
      val start = outputListVector.getElementStartIndex(rowIndex)
      val end = outputListVector.getElementEndIndex(rowIndex)
      Array<String>(end - start) {
        String(outputListInnerVector.get(it + start))
      }.asList()
    }

    result.put(sourceFile, SourceDescriptor(
      sourceFile = sourceFile,
      digest = digest,
      outputs = outputs,
    ))
    outputIndex++
  }

  return result
}