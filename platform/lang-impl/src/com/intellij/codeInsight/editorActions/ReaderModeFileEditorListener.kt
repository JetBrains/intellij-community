// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.editorActions

import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.applyReaderMode
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.instance
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.colors.EditorColorsListener
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFile.PROP_WRITABLE
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFilePropertyEvent

class ReaderModeFileEditorListener : FileEditorManagerListener {
  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>) {
    val project = source.project
    val fileEditor = editors.first.filterIsInstance<PsiAwareTextEditorImpl>().firstOrNull() ?: return

    file.fileSystem.addVirtualFileListener(object : VirtualFileListener {
      override fun propertyChanged(event: VirtualFilePropertyEvent) {
        if (event.propertyName == PROP_WRITABLE) {
          applyReaderMode(project, fileEditor.editor, file, fileIsOpenAlready = true, forceUpdate = true)
        }
      }
    }, fileEditor)

    if (!instance(project).enabled) return
    applyReaderMode(project, fileEditor.editor, file)

    ApplicationManager.getApplication().messageBus.connect(fileEditor)
      .subscribe(EditorColorsManager.TOPIC,
                 EditorColorsListener {
                   applyReaderMode(project, fileEditor.editor, file, fileIsOpenAlready = true, preferGlobalSettings = true)
                 })
  }
}