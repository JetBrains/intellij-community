// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.application.ApplicationManager
import java.io.File
import java.nio.file.Path

/**
 * Helper service to find a mapping between a file on the disk and [VirtualFile]
 */
interface VirtualFileLookup {
  /**
   * Enables refresh operation only for the part of the file system needed
   * for searching the file and finds file.
   *
   * This method is useful when the file was created externally and you need to find
   * [VirtualFile] corresponding to it.
   *
   * If the lookup is invoked not from Swing event dispatch thread, then it must not happen
   * inside a read action.
  */
  fun withRefresh() : VirtualFileLookup

  /**
   * Only checks if a given file is in caches of the respective
   * [com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem]
   */
  fun onlyIfCached() : VirtualFileLookup

  /**
   * Searches for a [VirtualFile] corresponding to the given [File]
   */
  fun fromIoFile(file: File) : VirtualFile?

  /**
   * Searches for a [VirtualFile] corresponding to the given nio [Path].
   * The [VirtualFile.toNioPath] method should return the same Path back.
   */
  fun fromNioPath(path: Path) : VirtualFile?

  /**
   * Searches for a [VirtualFile] corresponding to the given path in the local
   * filesystem. It's better to use either [fromNioPath] or [fromIoFile] methods
   */
  fun fromPath(path: String) : VirtualFile?

  /**
   * Searches for a file specified by the given [VirtualFile.getUrl] URL.
   * @return [VirtualFile] if the file was found, `null` otherwise
   *
   * @see VirtualFile.getUrl
   * @see VirtualFileSystem.findFileByPath
   * @see withRefresh
   */
  fun fromUrl(url: String) : VirtualFile?

  companion object {
    @JvmStatic
    fun newLookup(): VirtualFileLookup {
      return ApplicationManager
        .getApplication()
        .getService(VirtualFileLookupService::class.java)
        .newLookup()
    }
  }
}

interface VirtualFileLookupService {
  fun newLookup() : VirtualFileLookup
}
