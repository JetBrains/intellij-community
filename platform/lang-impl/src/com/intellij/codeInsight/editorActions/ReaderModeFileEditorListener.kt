// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent

private class ReaderModeFileEditorListener : FileOpenedSyncListener {
  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editorsWithProviders: List<FileEditorWithProvider>) {
    val project = source.project
    val fileEditor = editorsWithProviders.firstNotNullOfOrNull { it.fileEditor as? PsiAwareTextEditorImpl } ?: return

    val modalityState = ModalityState.stateForComponent(fileEditor.component)

    file.fileSystem.addVirtualFileListener(object : VirtualFileListener {
      override fun propertyChanged(event: VirtualFilePropertyEvent) {
        if (event.propertyName == VirtualFile.PROP_WRITABLE && event.file == file) {
          ApplicationManager.getApplication().invokeLater(Runnable {
            if (fileEditor.editor.isDisposed) return@Runnable

            ReaderModeSettings.applyReaderMode(project, fileEditor.editor, file, fileIsOpenAlready = true, forceUpdate = true)
          }, modalityState, project.disposed)
        }
      }
    }, fileEditor)

    if (!ReaderModeSettings.getInstance(project).enabled) {
      return
    }

    ReaderModeSettings.applyReaderMode(project, fileEditor.editor, file)

    ApplicationManager.getApplication().messageBus.connect(fileEditor).subscribe(EditorColorsManager.TOPIC, EditorColorsListener {
      ReaderModeSettings.applyReaderMode(project, fileEditor.editor, file, fileIsOpenAlready = true)
    })
  }
}