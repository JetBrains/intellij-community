// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus
import java.nio.ByteBuffer

@ApiStatus.Internal
sealed interface StreamingReadResult {
  interface Ok : StreamingReadResult {
    val chunk: ByteBuffer
  }

  interface Error : StreamingReadResult {
    val error: EelFileSystemApi.StreamingReadError
  }
}