// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ConstPropertyName", "DuplicatedCode")

package org.jetbrains.intellij.build.io

import org.jetbrains.intellij.build.io.ZipArchiveOutputStream.CompressedSizeAndCrc
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.CRC32
import java.util.zip.Deflater

private const val compressThreshold = 8 * 1024
private const val maxCompressChunkSize = 8L * 1024L * 1024L

fun writeZipUsingTempFile(file: Path, packageIndexBuilder: PackageIndexBuilder?, task: (ZipFileWriter) -> Unit) {
  writeFileUsingTempFile(file) { tempFile ->
    ZipFileWriter(ZipArchiveOutputStream(
      dataWriter = fileDataWriter(file = tempFile, overwrite = false, isTemp = true),
      zipIndexWriter = ZipIndexWriter(packageIndexBuilder),
    )).use {
      task(it)
    }
  }
}

inline fun writeNewZipWithoutIndex(file: Path, compress: Boolean = false, task: (ZipFileWriter) -> Unit) {
  Files.createDirectories(file.parent)
  ZipFileWriter(
    resultStream = zipWriter(targetFile = file, packageIndexBuilder = null),
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

  @Suppress("DuplicatedCode")
  fun file(nameString: String, file: Path) {
    if (crc32 == null) {
      resultStream.fileWithoutCrc(nameString.toByteArray(), file)
      return
    }

    FileChannel.open(file, READ_OPEN_OPTION).use { channel ->
      val size = channel.size().toInt()
      val isCompressed = size >= compressThreshold && deflater != null && !nameString.endsWith(".png")
      val path = nameString.toByteArray()
      if (isCompressed) {
        resultStream.writeMaybeCompressed(path = path, dataSize = size) { writer ->
          compressAndWriteFile(fileSize = size.toLong(), channel = channel, deflater = deflater, writer = writer, crc32 = crc32)
        }
      }
      else {
        resultStream.transferFromFileChannel(path = path, source = channel, size = size, crc32 = crc32)
      }
    }
  }

  fun compressedData(nameString: String, data: ByteBuffer) {
    compressedData(path = nameString, data = data, deflater = deflater!!, crc32 = crc32, resultStream = resultStream)
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
  writer: (ByteBuffer) -> Unit,
  crc32: CRC32,
): CompressedSizeAndCrc {
  val chunkSize = if (fileSize <= maxCompressChunkSize) {
    fileSize
  }
  else {
    (fileSize / (((fileSize + maxCompressChunkSize) - 1) / maxCompressChunkSize))
  }.toInt()

  val input = channel.map(MapMode.READ_ONLY, 0, fileSize)
  try {
    channel.close()

    crc32.compute(input)

    deflater.setInput(input)
    deflater.finish()

    val compressedSize = doDeflate(chunkSize = chunkSize, deflater = deflater, writer = writer)
    return CompressedSizeAndCrc(compressedSize, crc32.value)
  }
  finally {
    channel.close()
    unmapBuffer(input)
  }
}

private fun doDeflate(chunkSize: Int, deflater: Deflater, writer: (ByteBuffer) -> Unit): Int {
  var compressedSize = 0
  byteBufferAllocator.directBuffer(chunkSize).use { nettyOutput ->
    val output = nettyOutput.internalNioBuffer(nettyOutput.writerIndex(), chunkSize)!!

    val oldPosition = output.position()
    val oldLimit = output.limit()
    do {
      val n = deflater.deflate(output)
      if (n <= 0) {
        continue
      }

      output.limit(output.position())
      output.position(oldPosition)
      require((oldPosition + n) == output.limit())

      require(output.remaining() == n) {
        "Deflate must return `n` equal to `output.remaining()``" +
        " (remaining: ${output.remaining()}, n: $n, compressedSize: $compressedSize)" +
        " (oldPosition: $oldPosition, oldLimit: $oldLimit)" +
        " (chunkSize: $chunkSize, compressedSize: $compressedSize, n: $n)" +
        " (deflater: $deflater)"
      }

      compressedSize += n

      writer(output)
      output.limit(oldLimit)
      output.position(oldPosition)
    }
    while (!deflater.finished())
  }
  deflater.reset()
  return compressedSize
}

// visible for test
fun compressedData(path: String, data: ByteBuffer, deflater: Deflater, crc32: CRC32?, resultStream: ZipArchiveOutputStream) {
  resultStream.writeMaybeCompressed(path = path.toByteArray(), dataSize = data.remaining()) { writer ->
    val crc = crc32?.compute(data) ?: 0

    deflater.setInput(data)
    deflater.finish()

    val compressedSize = doDeflate(chunkSize = estimateDeflateBound(data.remaining()), deflater = deflater, writer = writer)
    CompressedSizeAndCrc(compressedSize, crc)
  }
}

private fun estimateDeflateBound(inputSize: Int): Int {
  return inputSize + (inputSize / 16) + 64 + 3
}
