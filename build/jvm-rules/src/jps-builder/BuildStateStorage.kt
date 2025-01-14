@file:Suppress("UnstableApiUsage")

package org.jetbrains.bazel.jvm.jps

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import com.intellij.openapi.util.io.BufferExposingByteArrayOutputStream
import org.jetbrains.intellij.build.io.unmapBuffer
import org.jetbrains.intellij.build.io.writeFileUsingTempFile
import org.jetbrains.jps.incremental.storage.PathTypeAwareRelativizer
import org.jetbrains.jps.incremental.storage.RelativePathType
import org.jetbrains.kotlin.utils.addToStdlib.enumSetOf
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.zip.CRC32

private const val STATE_FILE_FORMAT_VERSION = 1

private val WRITE_FILE_OPTION = enumSetOf(StandardOpenOption.WRITE, StandardOpenOption.CREATE)
private val READ_FILE_OPTION = enumSetOf(StandardOpenOption.READ)

internal fun loadBuildState(
  buildStateFile: Path,
  relativizer: PathTypeAwareRelativizer,
  log: RequestLog,
): HashMap<Path, SourceDescriptor>? {
  var map: MappedByteBuffer? = null
  try {
    map = FileChannel.open(buildStateFile, READ_FILE_OPTION).use {
      it.map(FileChannel.MapMode.READ_ONLY, 0, it.size())
    }
    return doLoad(map, relativizer)
  }
  catch (_: NoSuchFileException) {
    return null
  }
  catch (e: Throwable) {
    log.error("cannot load $buildStateFile", e)
    // will be deleted by caller
    return null
  }
  finally {
    map?.let { unmapBuffer(it) }
  }
}

internal fun saveBuildState(buildStateFile: Path, list: Array<SourceDescriptor>, relativizer: PathTypeAwareRelativizer) {
  val byteArrayOutputStream = BufferExposingByteArrayOutputStream()
  val output = CodedOutputStream.newInstance(byteArrayOutputStream)
  output.writeFixed32NoTag(STATE_FILE_FORMAT_VERSION)
  // allocate for checksum
  output.writeFixed64NoTag(0L)

  output.writeUInt32NoTag(list.size)
  for (descriptor in list) {
    output.writeStringNoTag(relativizer.toRelative(descriptor.sourceFile, RelativePathType.SOURCE))
    output.writeByteArrayNoTag(descriptor.digest)
    val outputs = descriptor.outputs
    if (outputs == null) {
      output.writeUInt32NoTag(0)
    }
    else {
      output.writeUInt32NoTag(outputs.size)
      for (outputPath in outputs) {
        output.writeStringNoTag(outputPath)
      }
    }
  }

  // allocate for checksum
  output.writeFixed64NoTag(0L)
  output.flush()

  val data = byteArrayOutputStream.internalBuffer
  val dataSize = byteArrayOutputStream.size()

  val crc32 = CRC32()
  val dataOffset = Int.SIZE_BYTES + Long.SIZE_BYTES
  crc32.update(data, dataOffset, dataSize - dataOffset)
  val checksum = crc32.value

  CodedOutputStream.newInstance(data, Int.SIZE_BYTES, dataOffset).writeFixed64NoTag(checksum)
  CodedOutputStream.newInstance(data, dataSize - Long.SIZE_BYTES, dataOffset).writeFixed64NoTag(checksum)

  val parent = buildStateFile.parent
  Files.createDirectories(parent)
  writeFileUsingTempFile(buildStateFile) { tempFile ->
    FileChannel.open(tempFile, WRITE_FILE_OPTION).use {
      it.write(ByteBuffer.wrap(data, 0, dataSize), 0)
    }
  }
}

private fun doLoad(byteBuffer: MappedByteBuffer, relativizer: PathTypeAwareRelativizer): HashMap<Path, SourceDescriptor> {
  val fileSize = byteBuffer.limit()

  val crc32 = CRC32()
  byteBuffer.mark()
  byteBuffer.position(Int.SIZE_BYTES + Long.SIZE_BYTES).limit(fileSize - Long.SIZE_BYTES)
  crc32.update(byteBuffer)
  val expectedChecksum = crc32.value
  byteBuffer.position(0).limit(fileSize)

  val input = CodedInputStream.newInstance(byteBuffer)

  val formatVersion = input.readRawLittleEndian32()
  if (formatVersion != STATE_FILE_FORMAT_VERSION) {
    throw IOException("format version mismatch: expected $STATE_FILE_FORMAT_VERSION, actual $formatVersion")
  }

  val storedChecksum = input.readRawLittleEndian64()
  if (storedChecksum != expectedChecksum) {
    throw IOException("checksum mismatch: expected $expectedChecksum, actual $storedChecksum (file start)")
  }

  val size = input.readRawVarint32()
  val map = HashMap<Path, SourceDescriptor>(size)
  repeat(size) {
    val source = input.readStringRequireUtf8()
    val digest = input.readByteArray()
    val inputSize = input.readRawVarint32()
    val outputs = if (inputSize == 0) {
      null
    }
    else {
      Array(inputSize) { input.readStringRequireUtf8() }.asList()
    }

    val sourceFile = relativizer.toAbsoluteFile(source, RelativePathType.SOURCE)
    map.put(sourceFile, SourceDescriptor(
      sourceFile = sourceFile,
      digest = digest,
      outputs = outputs,
    ))
  }

  val storedChecksumEnd = input.readRawLittleEndian64()
  if (storedChecksumEnd != expectedChecksum) {
    throw IOException("checksum mismatch: expected $expectedChecksum, actual $storedChecksumEnd (file end)")
  }

  return map
}