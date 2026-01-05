// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.StreamingWriteResult

object StreamingWriteResultImpl {
  class Ok(override val bytesWritten: Long) : StreamingWriteResult.Ok
  class Error(override val error: EelFileSystemApi.StreamingWriteError) : StreamingWriteResult.Error
}