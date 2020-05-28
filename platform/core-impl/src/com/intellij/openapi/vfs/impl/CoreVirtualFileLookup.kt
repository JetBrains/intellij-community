// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileLookup
import com.intellij.openapi.vfs.VirtualFileLookupService
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.util.io.URLUtil
import java.io.File
import java.nio.file.Path

object VirtualFileUrlLookupHelper {
  fun fromUrl(lazyVirtualFileManager: Lazy<VirtualFileManager>,
              withRefresh: Boolean,
              onlyIfCached: Boolean,
              url: String): VirtualFile? {
    if (onlyIfCached) return null

    val index = url.indexOf(URLUtil.SCHEME_SEPARATOR)
    if (index < 0) return null

    val protocol = url.substring(0, index)
    val fs = lazyVirtualFileManager.value.getFileSystem(protocol) ?: return null
    val path = url.substring(index + URLUtil.SCHEME_SEPARATOR.length)

    return when {
      withRefresh -> fs.refreshAndFindFileByPath(path)
      else -> fs.findFileByPath(path)
    }
  }
}

private data class CoreVirtualFileLookupImpl(
  private val lazyVirtualFileManager: Lazy<VirtualFileManager>,
  private val withRefresh: Boolean = false,
  private val onlyIfCached: Boolean = false
) : VirtualFileLookup {

  override fun withRefresh() = copy(withRefresh = true)
  override fun onlyIfCached() = copy(onlyIfCached = true)

  override fun fromIoFile(file: File): VirtualFile? = null
  override fun fromPath(path: String): VirtualFile? = null
  override fun fromNioPath(path: Path): VirtualFile? = null

  override fun fromUrl(url: String): VirtualFile? = VirtualFileUrlLookupHelper.fromUrl(lazyVirtualFileManager, withRefresh, onlyIfCached, url)
}

internal class CoreVirtualFileLookupServiceImpl : VirtualFileLookupService {
  private val lazyVirtualFileManager = lazy { VirtualFileManager.getInstance() }
  override fun newLookup() : VirtualFileLookup = CoreVirtualFileLookupImpl(lazyVirtualFileManager)
}
