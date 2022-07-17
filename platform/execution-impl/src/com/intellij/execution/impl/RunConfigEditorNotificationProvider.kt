// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

class RunConfigEditorNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel>() {
  private val KEY: Key<EditorNotificationPanel> = Key.create("RunConfigEditorNotificationProvider")

  override fun getKey(): Key<EditorNotificationPanel> = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (!file.name.endsWith(".run.xml")) return null
    if (!ProjectFileIndex.getInstance(project).isInContent(file)) return null

    val runManager = RunManagerImpl.getInstanceImpl(project)
    if (!runManager.isFileContainsRunConfiguration(file)) return null

    val panel = EditorNotificationPanel(fileEditor)
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

    return panel
  }
}