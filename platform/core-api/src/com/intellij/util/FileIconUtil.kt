// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.ide.FileIconPatcher
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Iconable.IconFlags
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon


object FileIconUtil {
  @JvmStatic
  fun getIconFromProviders(file: VirtualFile, @IconFlags flags: Int, project: Project?): Icon? {
    for (provider in FileIconProvider.EP_NAME.extensionList) {
      try {
        val icon = provider.getIcon(file, flags, project)
        if (icon != null) {
          return icon
        }
      }
      catch (_: IndexNotReadyException) {
      }
    }
    return null
  }

  @JvmStatic
  fun patchIconByIconPatchers(icon: Icon, file: VirtualFile, flags: Int, project: Project?): Icon {
    var patched = icon
    for (patcher in FileIconPatcher.EP_NAME.extensionList) {
      try {
        patched = patcher.patchIcon(patched, file, flags, project)
      }
      catch (_: IndexNotReadyException) {
      }
    }
    return patched
  }
}


