// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker

import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestReader
import org.jetbrains.bazel.jvm.doReadWorkRequestFromStream
import java.io.InputStream

internal class WorkRequestWithDigests(
  arguments: Array<String>,
  inputPaths: Array<String>,
  requestId: Int,
  cancel: Boolean,
  verbosity: Int,
  sandboxDir: String?,
  @JvmField val inputDigests: Array<ByteArray>,
) : WorkRequest(
  arguments = arguments,
  inputPaths = inputPaths,
  requestId = requestId,
  cancel = cancel,
  verbosity = verbosity,
  sandboxDir = sandboxDir,
)

internal open class WorkRequestWithDigestReader(
  //private val allocator: BufferAllocator,
  private val input: InputStream,
) : WorkRequestReader<WorkRequestWithDigests> {
  private val inputPathsToReuse = ArrayList<String>()
  private val inputDigestToReuse = ArrayList<ByteArray>()
  private val argListToReuse = ArrayList<String>()

  override fun readWorkRequestFromStream(): WorkRequestWithDigests? {
    val inputDigestToReuse = inputDigestToReuse
    inputDigestToReuse.clear()
    val result = doReadWorkRequestFromStream(
      input = input,
      inputPathsToReuse = inputPathsToReuse,
      argListToReuse = argListToReuse,
      readDigest = { codedInputStream, tag ->
        val digest = codedInputStream.readByteArray()
        //inputDigestToReuse.setSafe(counter++, digest)
        inputDigestToReuse.add(digest)
      },
      requestCreator = { argListToReuse, inputPathsToReuse, requestId, cancel, verbosity, sandboxDir ->
        //digests.setValueCount(counter)
        //counter = -1
        WorkRequestWithDigests(
          arguments = argListToReuse,
          inputPaths = inputPathsToReuse,
          requestId = requestId,
          cancel = cancel,
          verbosity = verbosity,
          sandboxDir = sandboxDir,
          inputDigests = inputDigestToReuse.toTypedArray(),
        )
      },
    )
    return result
  }
}
