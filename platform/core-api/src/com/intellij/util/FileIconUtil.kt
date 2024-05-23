// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ide.FileIconPatcher
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon


object FileIconUtil {
  @JvmStatic
  fun getIconFromProviders(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon? {
    for (provider in FileIconProvider.EP_NAME.extensionList) {
      val icon = kotlin.runCatching {
        provider.getIcon(file, flags, project)
      }.getOrLogException {
        if (it !is IndexNotReadyException) {
          LOG.warn("FileIconProvider $provider threw an exception", it)
        }
      }
      if (icon != null) {
        return icon
      }
    }
    return null
  }

  @JvmStatic
  fun patchIconByIconPatchers(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
    var patched = icon
    for (patcher in FileIconPatcher.EP_NAME.extensionList) {
      patched = kotlin.runCatching {
        patcher.patchIcon(patched, file, flags, project)
      }.getOrLogException {
        if (it !is IndexNotReadyException) {
          LOG.warn("FileIconPatcher $patcher threw an exception", it)
        }
      } ?: patched
    }
    return patched
  }
}

private val LOG = logger<FileIconUtil>()
