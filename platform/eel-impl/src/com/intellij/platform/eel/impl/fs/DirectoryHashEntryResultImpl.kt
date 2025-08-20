// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.DirectoryHashEntry
import com.intellij.platform.eel.fs.DirectoryHashEntryResult
import com.intellij.platform.eel.fs.EelFileSystemApi

object DirectoryHashEntryResultImpl {
  class Ok(override val value: DirectoryHashEntry) : DirectoryHashEntryResult.Ok
  class Error(override val error: EelFileSystemApi.DirectoryHashError) : DirectoryHashEntryResult.Error
}