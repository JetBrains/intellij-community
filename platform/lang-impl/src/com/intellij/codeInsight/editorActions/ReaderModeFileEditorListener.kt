// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.editorActions

import com.intellij.codeInsight.actions.ReaderModeSettings
import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.editor.ClientEditorManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileOpenedSyncListener
import com.intellij.openapi.fileEditor.ex.FileEditorWithProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.project.ex.ProjectManagerEx
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
            if (fileEditor.editor.isDisposed) {
              return@Runnable
            }

            ClientId.withExplicitClientId(ClientEditorManager.getClientId(fileEditor.editor)) {
              ReaderModeSettings.applyReaderMode(project, fileEditor.editor, file, fileIsOpenAlready = true, forceUpdate = true)
            }
          }, modalityState, project.disposed)
        }
      }
    }, fileEditor)

    if (!ReaderModeSettings.getInstance(project).enabled) {
      return
    }

    ReaderModeSettings.applyReaderMode(project, fileEditor.editor, file)
  }
}

private class ReaderModeEditorColorListener : EditorColorsListener {
  override fun globalSchemeChange(scheme: EditorColorsScheme?) {
    for (project in ProjectManagerEx.getOpenProjects()) {
      if (!ReaderModeSettings.getInstance(project).enabled) {
        continue
      }

      for (editor in (project.serviceIfCreated<FileEditorManager>() ?: continue).allEditors) {
        if (editor is PsiAwareTextEditorImpl) {
          ClientId.withExplicitClientId(ClientEditorManager.getClientId(editor.editor)) {
            ReaderModeSettings.applyReaderMode(project, editor.editor, editor.file, fileIsOpenAlready = true)
          }
        }
      }
    }
  }
}