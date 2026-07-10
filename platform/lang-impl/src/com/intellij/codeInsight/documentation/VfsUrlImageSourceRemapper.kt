// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

/**
 * Extension point to remap doc image URLs that cannot be resolved to a [VirtualFile][VirtualFile] via the standard VFS lookup.
 */
@ApiStatus.Internal
interface VfsUrlImageSourceRemapper {
  companion object {
    @JvmField
    val EP_NAME: ExtensionPointName<VfsUrlImageSourceRemapper> = ExtensionPointName("com.intellij.vfsUrlImageSourceRemapper")
  }

  /**
   * Attempts to remap the given VFS URL.
   * @return the [VirtualFile] the URL was remapped to, `null` otherwise
   */
  fun remap(vfsUrl: String): VirtualFile?
}
