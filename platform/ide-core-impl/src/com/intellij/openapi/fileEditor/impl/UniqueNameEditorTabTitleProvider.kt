// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettings.Companion.instanceOrNull
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

open class UniqueNameEditorTabTitleProvider : EditorTabTitleProvider {
  companion object {
    fun getEditorTabText(result: String?, separator: String?, hideKnownExtensionInTabs: Boolean): @NlsSafe String? {
      if (hideKnownExtensionInTabs) {
        val withoutExtension = FileUtilRt.getNameWithoutExtension(result!!)
        if (Strings.isNotEmpty(withoutExtension) && !withoutExtension.endsWith(separator!!)) {
          return withoutExtension
        }
      }
      return result
    }
  }

  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    val uiSettings = instanceOrNull
    if (uiSettings == null || !uiSettings.showDirectoryForNonUniqueFilenames || DumbService.isDumb(project)) {
      return null
    }

    // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
    var uniqueName: String = if (uiSettings.editorTabPlacement == UISettings.TABS_NONE) {
      UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePath(project, file)
    }
    else {
      UniqueVFilePathBuilder.getInstance().getUniqueVirtualFilePathWithinOpenedFileEditors(project, file)
    }
    uniqueName = getEditorTabText(uniqueName, File.separator, uiSettings.hideKnownExtensionInTabs)!!
    return if (uniqueName == file.name) null else uniqueName
  }
}