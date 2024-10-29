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
  ): Function<in FileEditor, out JComponent?> {
    val notification = getNotification(file, project) ?: return Function { null }

    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)

      panel.text = notification.text

      for (action in notification.actions) {
        panel.createActionLabel(action.text) { BrowserUtil.browse(action.link) }
      }

      panel
    }
  }
}

private fun getNotification(file: VirtualFile, project: Project): GeneratedSourceFilterNotification? {
  val matchingFilter = GeneratedSourcesFilter.findFirstMatchingFilter(file, project) ?: return null

  return  matchingFilter.getNotification(file, project)
            ?: GeneratedSourceFilterNotification(text = LangBundle.message("link.label.generated.source.files"),
                                                 actions = emptyList())
}
