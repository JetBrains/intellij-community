// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FileNameMatcherByIdService {
  /**
   * Compares file name with the given name using internal file name id api.
   * Every unique file name in VFS is given a unique id, which is much faster to access than the name itself.
   *
   * Comparison is always **case-sensitive**, even when the filesystem is not.
   *
   * @return `true` if file name matches given name
   *
   * `false` if it doesn't match
   *
   * `null` if a file doesn't have a name id
   */
  fun isFileNameMatches(virtualFile: VirtualFile, name: String): Boolean?

  companion object {
    @JvmStatic
    fun getInstance(): FileNameMatcherByIdService {
      return ApplicationManager.getApplication().getService(FileNameMatcherByIdService::class.java)
    }
  }
}