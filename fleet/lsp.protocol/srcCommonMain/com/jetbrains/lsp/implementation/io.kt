package com.jetbrains.lsp.implementation

import kotlinx.io.Buffer
import kotlinx.io.IOException
import kotlinx.io.InternalIoApi
import kotlinx.io.Sink
import kotlinx.io.Source
import kotlinx.io.readByteArray
import kotlinx.io.readString

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

private const val CR = 0x0D.toByte()
private const val LF = 0x0A.toByte()

// The implementation is derived from ByteReadChannel#readUTF8LineTo.
@OptIn(InternalIoApi::class)
suspend fun ByteReader.readUTF8Line(): String? {
  val out = StringBuilder()
  val completed = run {
    Buffer().use { lineBuffer ->
      while (!isClosedForRead) {
        while (!readBuffer.exhausted()) {
          when (val b = readBuffer.readByte()) {
            CR -> {
              // Check if LF follows CR after awaiting.
              if (readBuffer.exhausted()) awaitContent()
              if (readBuffer.buffer[0] == LF) {
                readBuffer.buffer.skip(1)
              }
              else {
                throw IOException("Unexpected line ending <CR>")
              }
              out.append(lineBuffer.readString())
              return@run true
            }

            LF -> {
              out.append(lineBuffer.readString())
              return@run true
            }

            else -> lineBuffer.writeByte(b)
          }
        }

        awaitContent()
      }

      (lineBuffer.size > 0).also { remaining ->
        if (remaining) {
          out.append(lineBuffer.readString())
        }
      }
    }
  }

  return if (completed) out.toString() else null
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
