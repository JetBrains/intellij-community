// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object TransientVirtualFileVfsRefreshUtils {

  /**
   * Use [NewVirtualFileSystem.findCachedOrTransientFileByPath] instead.
   *
   * This method must only be used for VFS refresh to avoid iterating over cached virtual files.
   *
   * Resulting child may already not exist on disk. Check `exists()` before usage
   */
  @JvmStatic
  fun createTransientChild(parent: VirtualFile, childName: String, fs: NewVirtualFileSystem): VirtualFile {
    val parentPath = parent.path
    val path = when {
      parentPath.endsWith('/') -> parent.path + childName
      else -> "$parentPath/$childName"
    }
    return TransientVirtualFileImpl(childName, path, fs, parent)
  }

  /**
   * @see [CacheAvoidingVirtualFile.isCached]
   */
  @JvmStatic
  fun isCached(file: VirtualFile): Boolean {
    return file is CacheAvoidingVirtualFile && file.isCached
  }
}