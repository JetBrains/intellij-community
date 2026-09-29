// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.backend.workspace.impl

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
public interface VirtualFileUrlWithVirtualFile {
  public fun cacheVirtualFile(file: VirtualFile)

  /**
   * Returns the file cached by [cacheVirtualFile] or by an earlier search, if VFS didn't change after that.
   * Otherwise, returns `null`. The function doesn't search VFS.
   */
  public fun getCachedVirtualFile(): VirtualFile?
}
