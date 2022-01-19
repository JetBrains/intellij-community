// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.applyReaderMode
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager

class ReaderModeEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project
    if (project == null || !project.isInitialized || project.isDefault || !ReaderModeSettings.getInstance(project).enabled) return
    if (editor !is EditorImpl) return

    applyReaderMode(project, editor, FileDocumentManager.getInstance().getFile(editor.document))
  }
}