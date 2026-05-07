// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.common.file.icons

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.fileTypes.PlainTextLikeFileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

internal class CommonFileIconsProvider : FileIconProvider {
  override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
    if (file.isDirectory || file.fileType !is PlainTextLikeFileType) {
      return null
    }

    return CommonFileIconsByExtension.find(file.extension)
  }

}