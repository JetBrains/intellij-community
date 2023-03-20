// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.lazyDumbAwareExtensions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

object EditorTabPresentationUtil {
  @JvmStatic
  fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String {
    return getCustomEditorTabTitle(project, file)
           ?: UniqueNameEditorTabTitleProvider().getEditorTabTitle(project, file)
           ?: file.presentableName
  }

  @JvmStatic
  fun getCustomEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    for (provider in EditorTabTitleProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      val result = provider.getEditorTabTitle(project, file)
      if (!result.isNullOrEmpty()) {
        return result
      }
    }
    return null
  }

  @JvmStatic
  fun getUniqueEditorTabTitle(project: Project, file: VirtualFile): String {
    val name = getEditorTabTitle(project, file)
    return if (name == file.presentableName) UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file) else name
  }

  @JvmStatic
  fun getEditorTabBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in EditorTabColorProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      provider.getEditorTabColor(project, file)?.let { return it }
    }
    return null
  }

  @JvmStatic
  fun getFileBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in EditorTabColorProvider.EP_NAME.lazyDumbAwareExtensions(project)) {
      provider.getProjectViewColor(project, file)?.let { return it }
    }
    return null
  }
}