package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedInputStream
import jdk.internal.util.xml.impl.Input
import java.io.FilterInputStream
import java.io.InputStream
import kotlin.math.min

class WorkRequest(
  @JvmField val arguments: Array<String>,
  @JvmField val inputPaths: Array<String>,
  @JvmField val inputDigests: Array<ByteArray>,
  @JvmField val requestId: Int,
  @JvmField val cancel: Boolean,
  @JvmField val verbosity: Int,
  @JvmField val sandboxDir: String?
)

internal fun readWorkRequestFromStream(
  input: InputStream,
  inputPathsToReuse: MutableList<String>,
  inputDigestsToReuse: MutableList<ByteArray>,
  argListToReuse: MutableList<String>
): WorkRequest? {
  // read the length-prefixed WorkRequest
  val firstByte = input.read()
  if (firstByte == -1) {
    return null
  }

  val size = CodedInputStream.readRawVarint32(firstByte, input)

  var requestId = 0
  var cancel = false
  var verbosity = 0
  var sandboxDir: String? = null

  val codedInputStream = CodedInputStream.newInstance(LimitedInputStream(input, size))
  argListToReuse.clear()
  inputPathsToReuse.clear()
  inputDigestsToReuse.clear()
  while (true) {
    val tag = codedInputStream.readTag()
    if (tag == 0) {
      break
    }

    when (tag.shr(3)) {
      1 -> argListToReuse.add(codedInputStream.readString())
      2 -> {
        var path = ""
        var digest: ByteArray? = null
        val messageSize = codedInputStream.readRawVarint32()
        val limit = codedInputStream.pushLimit(messageSize)
        while (true) {
          val tag = codedInputStream.readTag()
          if (tag == 0) {
            break
          }

          when (tag.shr(3)) {
            1 -> path = codedInputStream.readString()
            2 -> digest = codedInputStream.readByteArray()
            else -> codedInputStream.skipField(tag)
          }
        }
        codedInputStream.popLimit(limit)
        inputPathsToReuse.add(path)
        inputDigestsToReuse.add(digest!!)
      }
      3 -> requestId = codedInputStream.readInt32()
      4 -> cancel = codedInputStream.readBool()
      5 -> verbosity = codedInputStream.readInt32()
      6 -> sandboxDir = codedInputStream.readString()
      else -> codedInputStream.skipField(tag)
    }
  }

  return WorkRequest(
    arguments = argListToReuse.toTypedArray(),
    inputPaths = inputPathsToReuse.toTypedArray(),
    inputDigests = inputDigestsToReuse.toTypedArray(),
    requestId = requestId,
    cancel = cancel,
    verbosity = verbosity,
    sandboxDir = sandboxDir
  )
}

private class LimitedInputStream(input: InputStream, private var limit: Int) : FilterInputStream(input) {
  override fun available(): Int = min(super.available(), limit)

  override fun read(): Int {
    if (limit <= 0) {
      return -1
    }

    val result = super.read()
    if (result >= 0) {
      --limit
    }
    return result
  }

  override fun read(b: ByteArray?, off: Int, len: Int): Int {
    var len = len
    if (limit <= 0) {
      return -1
    }

    len = min(len, limit)
    val result = super.read(b, off, len)
    if (result >= 0) {
      limit -= result
    }
    return result
  }

  override fun skip(n: Long): Long {
    val result = super.skip(min(n, limit.toLong())).toInt()
    if (result >= 0) {
      limit -= result
    }
    return result.toLong()
  }
}