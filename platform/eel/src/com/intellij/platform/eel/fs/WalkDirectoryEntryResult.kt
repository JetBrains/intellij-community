// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.eel.fs

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface WalkDirectoryEntryResult {
  interface Ok : WalkDirectoryEntryResult {
    val value: WalkDirectoryEntry
  }

  interface Error : WalkDirectoryEntryResult {
    val error: EelFileSystemApi.WalkDirectoryError
  }
}