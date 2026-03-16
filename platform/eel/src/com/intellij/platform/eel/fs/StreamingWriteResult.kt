// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface StreamingWriteResult {
  interface Ok : StreamingWriteResult {
    val bytesWritten: Long
  }

  interface Error : StreamingWriteResult {
    val error: EelFileSystemApi.StreamingWriteError
  }
}