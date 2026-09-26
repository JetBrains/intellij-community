// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.LangBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

// TODO(IJPL-252267): a temporary link until the help page is published.
private const val ANALYSIS_IGNORE_HELP_URL: String = "https://www.jetbrains.com/help/idea/analysisignore.html"

private const val BANNER_DISMISSED_KEY: String = "analysis.ignore.banner.dismissed"

internal class AnalysisIgnoreEditorNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) return null
    if (!file.isAnalysisIgnoreFile()) return null
    if (PropertiesComponent.getInstance().getBoolean(BANNER_DISMISSED_KEY, false)) return null

    return Function { fileEditor ->
      EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Warning).apply {
        text = LangBundle.message("analysis.ignore.banner.text", ANALYSIS_IGNORE_FILE_NAME)
        // TODO: Uncomment when the doc page is ready
        //createActionLabel(LangBundle.message("analysis.ignore.banner.learn.more"), { BrowserUtil.browse(ANALYSIS_IGNORE_HELP_URL) }, false)
        setCloseAction { dismiss() }
      }
    }
  }

  private fun dismiss() {
    PropertiesComponent.getInstance().setValue(BANNER_DISMISSED_KEY, true, false)

    // The dismissal applies to the application. Every open project must remove the banner, not only the project of the editor.
    for (project in ProjectManager.getInstance().openProjects) {
      EditorNotifications.getInstance(project).updateNotifications(this)
    }
  }
}
