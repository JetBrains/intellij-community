// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.lang.LangBundle
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * Tells the user that the [defaults file][AnalysisIgnoreDefaultsFile] holds the exclusions of every project root without a
 * `.analysisignore` file. Thus a change of the file applies to each such root.
 */
internal class AnalysisIgnoreDefaultsFileNotificationProvider : EditorNotificationProvider, DumbAware {

  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (!AnalysisIgnoreDefaults.areEnabled()) return null
    if (file.name != ANALYSIS_IGNORE_DEFAULTS_FILE_NAME || !AnalysisIgnoreDefaultsFile.getInstance().isDefaultsFile(file)) return null

    return Function { fileEditor ->
      EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
        text = LangBundle.message("analysis.ignore.banner.defaults.file.text", ANALYSIS_IGNORE_FILE_NAME)
      }
    }
  }
}
