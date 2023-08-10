// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

private class RunConfigEditorNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!file.nameSequence.endsWith(".run.xml") || !ProjectFileIndex.getInstance(project).isInContent(file)) {
      return null
    }

    val runManager = RunManagerImpl.getInstanceImpl(project)
    if (!runManager.isFileContainsRunConfiguration(file)) {
      return null
    }

    return Function { fileEditor ->
      val panel = EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning)
      panel.text = ExecutionBundle.message("manual.editing.of.config.file.not.recommended")
      @Suppress("DialogTitleCapitalization") val message = ExecutionBundle.message("open.run.debug.dialog")
      panel.createActionLabel(message) {
        val oldSelectedConfig = runManager.selectedConfiguration

        runManager.selectConfigurationStoredInFile(file)
        val ok = EditConfigurationsDialog(project).showAndGet()

        if (!ok) {
          runManager.selectedConfiguration = oldSelectedConfig
        }
      }

      panel
    }
  }
}