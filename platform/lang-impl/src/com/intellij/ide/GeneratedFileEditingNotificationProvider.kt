// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.lang.LangBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

internal class GeneratedFileEditingNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile
  ): Function<in FileEditor, out JComponent?>? {
    if (!GeneratedSourceFileChangeTracker.getInstance(project).isEditedGeneratedFile(file)) {
      return null
    }

    val notificationText = getText(file, project)
    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
      panel.text = notificationText
      panel
    }
  }
}

private fun getText(file: VirtualFile, project: Project): @NlsContexts.LinkLabel String {
  if (project.isDisposed || !file.isValid) return LangBundle.message("link.label.generated.source.files")
  for (filter in GeneratedSourcesFilter.EP_NAME.extensionList) {
    if (!filter.isGeneratedSource(file, project)) {
      continue
    }

    val text = filter.getNotificationText(file, project)
    if (text != null) {
      return text
    }
  }
  return LangBundle.message("link.label.generated.source.files")
}
