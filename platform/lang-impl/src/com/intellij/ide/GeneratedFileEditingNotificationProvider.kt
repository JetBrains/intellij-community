// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.lang.LangBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourceFilterNotification
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

internal class GeneratedFileEditingNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<in FileEditor, out JComponent?>? {
    if (!GeneratedSourceFileChangeTracker.getInstance(project).isEditedGeneratedFile(file)) {
      return null
    }

    val notification = getNotification(file, project)
    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
      panel.text = notification.text
      for (action in notification.actions) {
        panel.createActionLabel(action.text) {
          BrowserUtil.browse(action.link)
        }
      }
      panel
    }
  }
}

private fun getNotification(file: VirtualFile, project: Project): GeneratedSourceFilterNotification {
  if (!project.isDisposed && file.isValid) {
    for (filter in GeneratedSourcesFilter.EP_NAME.extensionList) {
      if (!filter.isGeneratedSource(file, project)) {
        continue
      }

      filter.getNotification(file, project)?.let {
        return it
      }
    }
  }
  return GeneratedSourceFilterNotification(text = LangBundle.message("link.label.generated.source.files"), actions = emptyList())
}
