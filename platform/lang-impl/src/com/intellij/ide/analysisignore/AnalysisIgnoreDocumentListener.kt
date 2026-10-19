// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile

internal class AnalysisIgnoreDocumentListener : DocumentListener {

  override fun documentChanged(event: DocumentEvent) {
    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
    if (file is LightVirtualFile) return

    if (file.name == ANALYSIS_IGNORE_DEFAULTS_FILE_NAME) {
      // A service that does not exist yet reads the file when it starts.
      val defaultsFile = serviceIfCreated<AnalysisIgnoreDefaultsFile>()
      if (defaultsFile != null && defaultsFile.isDefaultsFile(file)) defaultsFile.reload()
      return
    }

    if (!file.isAnalysisIgnoreFile()) return
    for (project in ProjectManager.getInstance().openProjects) {
      AnalysisIgnoreService.getInstance(project).scheduleChanges(files = listOf(file))
    }
  }
}
