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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.File

open class UniqueNameEditorTabTitleProvider : EditorTabTitleProvider {
  override fun getEditorTabTitle(project: Project, file: VirtualFile): String? = doGetUniqueNameEditorTabTitle(project, file)
}

abstract class CustomisableUniqueNameEditorTabTitleProvider : EditorTabTitleProvider {
  abstract fun isApplicable(file: VirtualFile): Boolean

  @NlsContexts.TabTitle
  abstract fun getEditorTabTitle(file: VirtualFile, @Nls baseUniqueName: String): String

  final override fun getEditorTabTitle(project: Project, file: VirtualFile): String? {
    if (isApplicable(file)) {
      val baseUniqueName = getBaseUniqueName(project, file) ?: return null
      return getEditorTabTitle(file, baseUniqueName)
    }
    return null
  }

  private fun getBaseUniqueName(project: Project, file: VirtualFile): @Nls String? {
    var baseName = doGetUniqueNameEditorTabTitle(project, file)
    if (baseName == null && UISettings.getInstance().hideKnownExtensionInTabs && !file.isDirectory) {
      baseName = file.nameWithoutExtension.ifEmpty { file.name }
    }
    if (baseName == file.presentableName) return null
    return baseName
  }
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
  if (!shouldComputeUniqueTabNames(project)) {
    return null
  }

  // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
  val uiSettings = UISettings.getInstance()
  val uniqueFilePathBuilder = UniqueVFilePathBuilder.getInstance()
  var uniqueName = ReadAction.computeBlocking<String, Throwable> {
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
  if (!shouldComputeUniqueTabNames(project)) {
    return null
  }

  // Even though this is a 'tab title provider' it is used also when tabs are not shown, namely for building IDE frame title.
  val uiSettings = UISettings.getInstance()
  val uniqueFilePathBuilder = (ApplicationManager.getApplication() as ComponentManagerEx)
                                .getServiceAsyncIfDefined(UniqueVFilePathBuilder::class.java) ?: return null
  val builder = uniqueFilePathBuilder.withProject(project) // preload necessary services out of read action

  var uniqueName = readAction {
    if (uiSettings.editorTabPlacement == UISettings.TABS_NONE) {
      builder.getUniqueVirtualFilePath(file)
    }
    else {
      builder.getUniqueVirtualFilePathWithinOpenedFileEditors(file)
    }
  }

  uniqueName = getEditorTabText(
    result = uniqueName,
    separator = File.separator,
    hideKnownExtensionInTabs = uiSettings.hideKnownExtensionInTabs,
  )
  return uniqueName.takeIf { uniqueName != file.name }
}

private fun shouldComputeUniqueTabNames(project: Project): Boolean {
  val uiSettings = UISettings.instanceOrNull
  if (uiSettings == null || !uiSettings.showDirectoryForNonUniqueFilenames) return false
  if (DumbService.isDumb(project)) return false
  return true
}
