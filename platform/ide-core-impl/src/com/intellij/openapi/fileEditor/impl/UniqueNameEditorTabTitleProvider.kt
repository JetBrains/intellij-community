// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.fileEditor.UniqueVFilePathBuilder
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.File

open class UniqueNameEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? = doGetUniqueNameEditorTabTitle(project, file)
}

@ApiStatus.Internal
fun getEditorTabText(result: String, separator: String, hideKnownExtensionInTabs: Boolean): @NlsSafe String {
  if (hideKnownExtensionInTabs) {
    val withoutExtension = FileUtilRt.getNameWithoutExtension(result)
    if (!withoutExtension.isEmpty() && !withoutExtension.endsWith(separator)) {
      return withoutExtension
    }
  }
  return result
}

@NlsSafe
internal fun doGetUniqueNameEditorTabTitle(project: Project, file: VirtualFile): String? {
  val uiSettings = UISettings.instanceOrNull
  if (uiSettings == null || !uiSettings.showDirectoryForNonUniqueFilenames || DumbService.isDumb(project)) {
    return null
  }

  // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
  val uniqueFilePathBuilder = UniqueVFilePathBuilder.getInstance()
  var uniqueName = ReadAction.compute<String, Throwable> {
    if (uiSettings.editorTabPlacement == UISettings.TABS_NONE) {
      uniqueFilePathBuilder.getUniqueVirtualFilePath(project, file)
    }
    else {
      uniqueFilePathBuilder.getUniqueVirtualFilePathWithinOpenedFileEditors(project, file)
    }
  }
  uniqueName = getEditorTabText(
    result = uniqueName,
    separator = File.separator,
    hideKnownExtensionInTabs = uiSettings.hideKnownExtensionInTabs,
  )
  return uniqueName.takeIf { uniqueName != file.name }
}

@NlsSafe
internal suspend fun getUniqueNameEditorTabTitleAsync(project: Project, file: VirtualFile): String? {
  val uiSettings = UISettings.instanceOrNull
  if (uiSettings == null || !uiSettings.showDirectoryForNonUniqueFilenames || DumbService.isDumb(project)) {
    return null
  }

  // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
  val uniqueFilePathBuilder = (ApplicationManager.getApplication() as ComponentManagerEx)
                                .getServiceAsyncIfDefined(UniqueVFilePathBuilder::class.java) ?: return null
  var uniqueName = readAction {
    if (uiSettings.editorTabPlacement == UISettings.TABS_NONE) {
      uniqueFilePathBuilder.getUniqueVirtualFilePath(project, file)
    }
    else {
      uniqueFilePathBuilder.getUniqueVirtualFilePathWithinOpenedFileEditors(project, file)
    }
  }
  uniqueName = getEditorTabText(
    result = uniqueName,
    separator = File.separator,
    hideKnownExtensionInTabs = uiSettings.hideKnownExtensionInTabs,
  )
  return uniqueName.takeIf { uniqueName != file.name }
}