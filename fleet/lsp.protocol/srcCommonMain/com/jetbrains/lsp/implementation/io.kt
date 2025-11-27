package com.jetbrains.lsp.implementation

import kotlinx.io.*

/** Replicates the ByteReadChannel interface from ktor utils with the same semantics. */
interface ByteReader {
  val closedCause: Throwable?
  val isClosedForRead: Boolean
  val readBuffer: Source

  suspend fun awaitContent(min: Int = 1): Boolean
  fun cancel(cause: Throwable?)
}

/** Replicates the ByteWriteChannel interface from ktor utils with the same semantics. */
interface ByteWriter {
  val isClosedForWrite: Boolean
  val closedCause: Throwable?
  val writeBuffer: Sink

  suspend fun flush()
  suspend fun flushAndClose()
  fun cancel(cause: Throwable?)
}

fun ByteWriter.writeByteArray(array: ByteArray) {
  writeBuffer.write(array)
}

fun ByteReader.cancel() {
  cancel(IOException("Channel was cancelled"))
}

suspend fun ByteReader.readUTF8Line(): String? {
  val builder = StringBuilder()
  do {
    val linefeed = readBuffer.indexOf(0x0A)
    if (linefeed != -1L) {
      builder.append(readBuffer.readString(linefeed))
      if (builder.isNotEmpty() && builder[builder.length - 1] == '\r') {
        builder.deleteAt(builder.length - 1)
      }
      check(readBuffer.readByte() == 0x0A.toByte()) { "expected to see the previously found line terminator" }

      return builder.toString()
    }

    builder.append(readBuffer.readString())
  }
  while (awaitContent())

  // Line terminator was never found before the byte stream was closed.
  return null
}

suspend fun ByteReader.readByteArray(count: Int): ByteArray {
  val buffer = Buffer()
  while (buffer.size != count.toLong()) {
    if (readBuffer.exhausted()) awaitContent()
    if (isClosedForRead) break

    readBuffer.readAtMostTo(buffer, count - buffer.size)
  }
  return buffer.readByteArray()
}
