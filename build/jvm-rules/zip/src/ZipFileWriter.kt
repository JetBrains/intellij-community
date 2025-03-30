// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName", "DuplicatedCode")

package org.jetbrains.intellij.build.io

import org.jetbrains.intellij.build.io.ZipArchiveOutputStream.CompressedSizeAndCrc
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.zip.CRC32
import java.util.zip.Deflater
import kotlin.math.min

private const val compressThreshold = 8 * 1024
// 8 MB (as JDK)
private const val mappedTransferSize = 8L * 1024L * 1024L

fun transformZipUsingTempFile(file: Path, packageIndexBuilder: PackageIndexBuilder?, task: (ZipFileWriter) -> Unit) {
  val tempFile = Files.createTempFile(file.parent, file.fileName.toString(), ".tmp")
  try {
    ZipFileWriter(ZipArchiveOutputStream(dataWriter = fileDataWriter(tempFile), zipIndexWriter = ZipIndexWriter(packageIndexBuilder))).use {
      task(it)
    }
    Files.move(tempFile, file, StandardCopyOption.REPLACE_EXISTING)
  }
  finally {
    Files.deleteIfExists(tempFile)
  }
}

inline fun writeNewZipWithoutIndex(file: Path, compress: Boolean = false, task: (ZipFileWriter) -> Unit) {
  Files.createDirectories(file.parent)
  ZipFileWriter(
    ZipArchiveOutputStream(fileDataWriter(file, W_OVERWRITE), ZipIndexWriter(null)),
    deflater = if (compress) Deflater(Deflater.DEFAULT_COMPRESSION, true) else null,
  ).use {
    task(it)
  }
}

// you must pass SeekableByteChannel if files are written (`file` method)
class ZipFileWriter(
  @JvmField internal val resultStream: ZipArchiveOutputStream,
  useCrc: Boolean = true,
  private val deflater: Deflater? = null,
) : AutoCloseable {
  private val crc32: CRC32? = if (useCrc) CRC32() else null

  val channelPosition: Long
    get() = resultStream.getChannelPosition()

  @Suppress("DuplicatedCode")
  fun file(nameString: String, file: Path) {
    if (crc32 == null) {
      resultStream.fileWithoutCrc(nameString.toByteArray(), file)
      return
    }

    FileChannel.open(file, READ).use { channel ->
      val size = channel.size().toInt()
      val isCompressed = size >= compressThreshold && deflater != null && !nameString.endsWith(".png")
      val path = nameString.toByteArray()
      if (isCompressed) {
        resultStream.writeMaybeCompressed(path = path, dataSize = size) { buffer ->
          compressAndWriteFile(fileSize = size.toLong(), channel = channel, deflater = deflater, resultBuffer = buffer, crc32 = crc32)
        }
      }
      else {
        resultStream.transferFromFileChannel(path, channel, size, crc32)
      }
    }
  }

  fun compressedData(nameString: String, data: ByteBuffer) {
    compressedData(
      path = nameString,
      data = data,
      deflater = deflater!!,
      crc32 = crc32!!,
      resultStream = resultStream,
    )
  }

  fun uncompressedData(nameString: String, data: String) {
    uncompressedData(path = nameString, data = data.toByteArray())
  }

  fun uncompressedData(path: String, data: ByteArray) {
    resultStream.uncompressedData(path.toByteArray(), data, crc32)
  }

  fun uncompressedData(path: String, data: ByteBuffer) {
    resultStream.uncompressedData(path.toByteArray(), data, crc32)
  }

  override fun close() {
    @Suppress("ConvertTryFinallyToUseCall")
    try {
      deflater?.end()
    }
    finally {
      resultStream.close()
    }
  }
}

private fun compressAndWriteFile(
  fileSize: Long,
  channel: FileChannel,
  deflater: Deflater,
  resultBuffer: ByteBuffer,
  crc32: CRC32,
): CompressedSizeAndCrc {
  var remaining = fileSize
  var position = 0L
  var compressedSize = 0L

  val oldOutPosition = resultBuffer.position()
  val oldOutLimit = resultBuffer.limit()

  crc32.reset()
  while (remaining > 0L) {
    val size = min(remaining, mappedTransferSize)
    val buffer = channel.map(MapMode.READ_ONLY, position, size)

    remaining -= size
    position += size

    try {
      buffer.mark()
      crc32.update(buffer)
      buffer.reset()

      deflater.setInput(buffer)

      // deflate until input buffer is exhausted
      while (!deflater.needsInput()) {
        val n = deflater.deflate(resultBuffer, Deflater.NO_FLUSH)
        if (n > 0) {
          compressedSize += n
        }
        else if (n == 0 && deflater.needsInput()) {
          // deflater needs more input, break to read more data
          break
        }
        else if (n == 0 && !resultBuffer.hasRemaining()) {
          throw IllegalStateException("Output buffer is full ($resultBuffer")
        }
      }
    }
    finally {
      unmapBuffer(buffer)
    }
  }

  // finish deflation
  deflater.finish()
  while (!deflater.finished()) {
    val n = deflater.deflate(resultBuffer, Deflater.NO_FLUSH)
    if (n > 0) {
      compressedSize += n
    }
  }
  deflater.reset()

  resultBuffer.limit(resultBuffer.position())
  resultBuffer.position(oldOutPosition)

  if ((fileSize - compressedSize) < 4096) {
    resultBuffer.limit(oldOutLimit)
    resultBuffer.position(oldOutPosition)
    // incompressible
    return CompressedSizeAndCrc(-1, crc32.value)
  }

  return CompressedSizeAndCrc(compressedSize.toInt(), crc32.value)
}

// visible for test
fun compressedData(path: String, data: ByteBuffer, deflater: Deflater, crc32: CRC32, resultStream: ZipArchiveOutputStream) {
  resultStream.writeMaybeCompressed(path = path.toByteArray(), dataSize = data.remaining()) { output ->
    val crc = crc32.compute(data)

    deflater.setInput(data)
    deflater.finish()
    var compressedSize = 0
    do {
      val n = deflater.deflate(output, Deflater.SYNC_FLUSH)
      assert(n != 0)
      compressedSize += n
    }
    while (data.hasRemaining())
    deflater.reset()
    CompressedSizeAndCrc(compressedSize, crc)
  }
}
