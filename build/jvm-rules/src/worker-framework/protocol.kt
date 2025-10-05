// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm

import com.google.protobuf.CodedInputStream
import io.netty.buffer.ByteBufAllocator
import java.io.InputStream

// https://github.com/bazelbuild/bazel/blob/8.2.1/src/main/protobuf/worker_protocol.proto#L22
class Input(
  @JvmField val path: String,
  @JvmField val digest: ByteArray?,
)

// https://github.com/bazelbuild/bazel/blob/8.2.1/src/main/protobuf/worker_protocol.proto#L36
class WorkRequest(
  @JvmField val arguments: Array<String>,
  @JvmField val inputs: Array<Input>,
  @JvmField val requestId: Int,
  @JvmField val cancel: Boolean,
  @JvmField val verbosity: Int,
  @JvmField val sandboxDir: String?,
)

interface WorkRequestReader {
  fun readWorkRequestFromStream(): WorkRequest?
}

class WorkRequestReaderWithoutDigest(private val input: InputStream) : WorkRequestReader {
  override fun readWorkRequestFromStream(): WorkRequest? {
    return doReadWorkRequestFromStream(
      input = input,
      shouldReadDigest = false,
    )
  }
}

fun doReadWorkRequestFromStream(
  input: InputStream,
  shouldReadDigest: Boolean,
): WorkRequest? {
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

    var arguments = ArrayList<String>()
    var inputs = ArrayList<Input>()
    var requestId = 0
    var cancel = false
    var verbosity = 0
    var sandboxDir: String? = null

    val codedInputStream = CodedInputStream.newInstance(buffer.array(), buffer.arrayOffset() + buffer.readerIndex(), buffer.readableBytes())
    while (true) {
      val tag = codedInputStream.readTag()
      if (tag == 0) {
        break
      }

      when (tag.shr(3)) {
        1 -> arguments.add(codedInputStream.readString())
        2 -> {
          val messageSize = codedInputStream.readRawVarint32()
          val limit = codedInputStream.pushLimit(messageSize)
          readInput(codedInputStream, shouldReadDigest)?.let(inputs::add)
          codedInputStream.popLimit(limit)
        }

        3 -> requestId = codedInputStream.readInt32()
        4 -> cancel = codedInputStream.readBool()
        5 -> verbosity = codedInputStream.readInt32()
        6 -> sandboxDir = codedInputStream.readString()
        else -> codedInputStream.skipField(tag)
      }
    }

    return WorkRequest(
      arguments = arguments.toArray(emptyStringArray),
      inputs = inputs.toArray(emptyInputArray),
      requestId = requestId,
      cancel = cancel,
      verbosity = verbosity,
      sandboxDir = sandboxDir,
    )
  }
  finally {
    buffer.release()
  }
}

// ctx.file._jvm_builder_launcher and ctx.file._jvm_builder are declared as tools in jvm_library rule (see `isTool` in the action's inputs):
// {
//   "path": "external/rules_jvm+/rules/impl/MemoryLauncher.java",
//   "digest": {
//     "hash": "d5d7b879e969a2e8f9c293a2fcca83636bab22fbfe26e4834242fe3b25287392",
//     "sizeBytes": "3598",
//     "hashFunctionName": "SHA-256"
//   },
//   "isTool": true,
//   "symlinkTargetPath": ""
// }
// but the worker request's Input message does not include any `is_tool` field (https://github.com/bazelbuild/bazel/blob/8.2.1/src/main/protobuf/worker_protocol.proto#L22)
// and these tools are read as ordinary inputs that the worker then tries to compile, etc.
//
// Same for ctx.file._worker_launcher and ctx.file._worker in jvm_resources rule.
private val KnownInputsThatAreTools = setOf(
  // _jvm_builder attribute in jvm_library rule
  "bazel-out/scrubbed_host-fastbuild/bin/external/community+/monorepo-jvm-builder_deploy.jar",
  "bazel-out/scrubbed_host-fastbuild/bin/external/rules_jvm+/jvm-inc-builder/jvm-inc-builder_deploy.jar",
  "bazel-out/scrubbed_host-fastbuild/bin/external/rules_jvm+/src/jvm-builder/jvm-builder_deploy.jar",

  // _worker attribute in jvm_resources rule
  "bazel-out/scrubbed_host-fastbuild/bin/external/rules_jvm+/src/misc/worker-jvm_deploy.jar",

  // _jvm_builder_launcher attribute in jvm_library rule and _worker_launcher attribute in jvm_resources rule
  "external/rules_jvm+/rules/impl/MemoryLauncher.java",
)

@PublishedApi
internal fun readInput(
  codedInputStream: CodedInputStream,
  shouldReadDigest: Boolean,
): Input? {
  var path: String? = null
  var digest: ByteArray? = null

  while (true) {
    val tag = codedInputStream.readTag()
    if (tag == 0) {
      break
    }

    when (tag.shr(3)) {
      1 -> path = codedInputStream.readString()
      2 -> {
        if (shouldReadDigest) {
          digest = codedInputStream.readByteArray()
        }
        else {
          codedInputStream.skipField(tag)
        }
      }

      else -> codedInputStream.skipField(tag)
    }
  }

  if (path in KnownInputsThatAreTools) {
    return null  // ignore
  }
  return Input(path!!, digest)
}

@PublishedApi
internal val emptyStringArray = emptyArray<String>()

@PublishedApi
internal val emptyInputArray = emptyArray<Input>()
