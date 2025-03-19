// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedInputStream
import io.netty.buffer.ByteBufAllocator
import java.io.InputStream

open class WorkRequest(
  @JvmField val arguments: Array<String>,
  @JvmField val inputPaths: Array<String>,
  @JvmField val requestId: Int,
  @JvmField val cancel: Boolean,
  @JvmField val verbosity: Int,
  @JvmField val sandboxDir: String?
)

interface WorkRequestReader<T : WorkRequest> {
  fun readWorkRequestFromStream(): T?
}

class WorkRequestReaderWithoutDigest(private val input: InputStream) : WorkRequestReader<WorkRequest> {
  private val inputPathsToReuse = ArrayList<String>()
  private val argListToReuse = ArrayList<String>()

  override fun readWorkRequestFromStream(): WorkRequest? {
    return doReadWorkRequestFromStream(
      input = input,
      inputPathsToReuse = inputPathsToReuse,
      argListToReuse = argListToReuse,
      readDigest = { codedInputStream, tag -> codedInputStream.skipField(tag) },
      requestCreator = { argListToReuse, inputPathsToReuse, requestId, cancel, verbosity, sandboxDir ->
        WorkRequest(
          arguments = argListToReuse,
          inputPaths = inputPathsToReuse,
          requestId = requestId,
          cancel = cancel,
          verbosity = verbosity,
          sandboxDir = sandboxDir
        )
      },
    )
  }
}

inline fun <T : WorkRequest> doReadWorkRequestFromStream(
  input: InputStream,
  inputPathsToReuse: MutableList<String>,
  argListToReuse: MutableList<String>,
  readDigest: (CodedInputStream, Int) -> Unit,
  requestCreator: RequestCreator<T>,
): T? {
  // read the length-prefixed WorkRequest
  val firstByte = input.read()
  if (firstByte == -1) {
    return null
  }

  val size = CodedInputStream.readRawVarint32(firstByte, input)
  val buffer = ByteBufAllocator.DEFAULT.heapBuffer(size, size)
  try {
    var toRead = size
    while (toRead > 0) {
      val n = buffer.writeBytes(input, toRead)
      if (n < 0) {
        throw IllegalStateException("Unexpected EOF")
      }
      toRead -= n
    }
    assert(buffer.readableBytes() == size)

    var requestId = 0
    var cancel = false
    var verbosity = 0
    var sandboxDir: String? = null

    val codedInputStream = CodedInputStream.newInstance(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(), buffer.readableBytes())
    argListToReuse.clear()
    inputPathsToReuse.clear()
    while (true) {
      val tag = codedInputStream.readTag()
      if (tag == 0) {
        break
      }

      when (tag.shr(3)) {
        1 -> argListToReuse.add(codedInputStream.readString())
        2 -> {
          val messageSize = codedInputStream.readRawVarint32()
          val limit = codedInputStream.pushLimit(messageSize)
          readInput(codedInputStream, inputPathsToReuse, readDigest)
          codedInputStream.popLimit(limit)
        }

        3 -> requestId = codedInputStream.readInt32()
        4 -> cancel = codedInputStream.readBool()
        5 -> verbosity = codedInputStream.readInt32()
        6 -> sandboxDir = codedInputStream.readString()
        else -> codedInputStream.skipField(tag)
      }
    }

    return requestCreator(
      (argListToReuse as java.util.ArrayList).toArray(emptyStringArray),
      (inputPathsToReuse as java.util.ArrayList).toArray(emptyStringArray),
      requestId,
      cancel,
      verbosity,
      sandboxDir,
    )
  }
  finally {
    buffer.release()
  }
}

@PublishedApi
internal inline fun readInput(
  codedInputStream: CodedInputStream,
  inputPathsToReuse: MutableList<String>,
  readDigest: (CodedInputStream, Int) -> Unit
) {
  while (true) {
    val tag = codedInputStream.readTag()
    if (tag == 0) {
      break
    }

    when (tag.shr(3)) {
      1 -> inputPathsToReuse.add(codedInputStream.readString())
      2 -> readDigest(codedInputStream, tag)
      else -> codedInputStream.skipField(tag)
    }
  }
}

@PublishedApi
internal val emptyStringArray = emptyArray<String>()

typealias RequestCreator<T> = (
  argListToReuse: Array<String>,
  inputPathsToReuse: Array<String>,
  requestId: Int,
  cancel: Boolean,
  verbosity: Int,
  sandboxDir: String?
) -> T
