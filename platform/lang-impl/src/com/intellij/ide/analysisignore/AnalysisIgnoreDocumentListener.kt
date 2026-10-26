// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.LightVirtualFile

internal class AnalysisIgnoreDocumentListener : DocumentListener {

  override fun documentChanged(event: DocumentEvent) {
    val openProjects = ProjectManager.getInstance().openProjects
    if (openProjects.isEmpty()) return

    val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
    if (file is LightVirtualFile || !file.isAnalysisIgnoreFile()) return

    for (project in openProjects) {
      AnalysisIgnoreService.getInstance(project).scheduleChanges(files = listOf(file))
    }
  }
}
