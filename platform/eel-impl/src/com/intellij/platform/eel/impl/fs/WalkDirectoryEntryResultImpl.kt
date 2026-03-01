// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.impl.fs

import com.intellij.platform.eel.fs.EelFileSystemApi
import com.intellij.platform.eel.fs.WalkDirectoryEntry
import com.intellij.platform.eel.fs.WalkDirectoryEntryResult

object WalkDirectoryEntryResultImpl {
  class Ok(override val value: WalkDirectoryEntry) : WalkDirectoryEntryResult.Ok
  class Error(override val error: EelFileSystemApi.WalkDirectoryError) : WalkDirectoryEntryResult.Error
}