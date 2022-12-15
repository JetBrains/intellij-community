// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color

object EditorTabPresentationUtil {
  @JvmStatic
  fun getEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String {
    val overriddenTitle = getCustomEditorTabTitle(project, file)
    if (overriddenTitle != null) {
      return overriddenTitle
    }
    val uniqueTitle = UniqueNameEditorTabTitleProvider().getEditorTabTitle(project, file)
    return uniqueTitle ?: file.presentableName
  }

  fun getCustomEditorTabTitle(project: Project, file: VirtualFile): @NlsContexts.TabTitle String? {
    for (provider in DumbService.getDumbAwareExtensions(project, EditorTabTitleProvider.EP_NAME)) {
      val result = provider.getEditorTabTitle(project, file)
      if (Strings.isNotEmpty(result)) {
        return result
      }
    }
    return null
  }

  fun getUniqueEditorTabTitle(project: Project, file: VirtualFile): String {
    val name = getEditorTabTitle(project, file)
    return if (name == file.presentableName) {
      UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file)
    }
    else name
  }

  @JvmStatic
  fun getEditorTabBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in DumbService.getDumbAwareExtensions(project, EditorTabColorProvider.EP_NAME)) {
      val result = provider.getEditorTabColor(project, file)
      if (result != null) {
        return result
      }
    }
    return null
  }

  fun getFileBackgroundColor(project: Project, file: VirtualFile): Color? {
    for (provider in DumbService.getDumbAwareExtensions(project, EditorTabColorProvider.EP_NAME)) {
      val result = provider.getProjectViewColor(project, file)
      if (result != null) {
        return result
      }
    }
    return null
  }
}