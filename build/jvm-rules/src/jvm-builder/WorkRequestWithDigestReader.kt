// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.worker

import org.jetbrains.bazel.jvm.WorkRequest
import org.jetbrains.bazel.jvm.WorkRequestReader
import org.jetbrains.bazel.jvm.doReadWorkRequestFromStream
import java.io.InputStream

internal open class WorkRequestWithDigestReader(
  //private val allocator: BufferAllocator,
  private val input: InputStream,
) : WorkRequestReader {
  override fun readWorkRequestFromStream(): WorkRequest? {
    return doReadWorkRequestFromStream(
      input = input,
      shouldReadDigest = true,
    )
  }
}
