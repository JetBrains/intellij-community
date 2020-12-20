// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.actions

import com.intellij.codeInsight.actions.ReaderModeFileEditorListener.Companion.applyReaderMode
import com.intellij.codeInsight.actions.ReaderModeSettings.Companion.instance
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.PsiAwareTextEditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.FileIndexFacade
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager

class ReaderModeFileEditorListener : FileEditorManagerListener {
  override fun fileOpenedSync(source: FileEditorManager, file: VirtualFile, editors: Pair<Array<FileEditor>, Array<FileEditorProvider>>) {
    if (!instance(source.project).enabled) return
    val selectedEditor = source.getSelectedEditor(file)
    if (selectedEditor !is PsiAwareTextEditorImpl) return

    applyReaderMode(source.project, selectedEditor.editor, file)
  }

  companion object {
    private var EP_READER_MODE_PROVIDER = ExtensionPointName<ReaderModeProvider>("com.intellij.readerModeProvider")

    fun applyReaderMode(project: Project, editor: Editor?, file: VirtualFile?, fileIsOpenAlready: Boolean = false) {
      if (editor == null || file == null || PsiManager.getInstance(project).findFile(file) == null) return

      if (matchMode(project, file)) {
        EP_READER_MODE_PROVIDER.extensions().forEach {
          it.applyModeChanged(project, editor, instance(project).enabled, fileIsOpenAlready)
        }
      }
    }

    fun matchMode(project: Project?, file: VirtualFile?): Boolean {
      if (project == null || file == null) return false

      val inLibraries = FileIndexFacade.getInstance(project).isInLibraryClasses(file) || FileIndexFacade.getInstance(project).isInLibrarySource(file)
      val isWritable = file.isWritable

      return when (instance(project).mode) {
        ReaderMode.LIBRARIES_AND_READ_ONLY -> inLibraries || !isWritable
        ReaderMode.LIBRARIES -> inLibraries
        ReaderMode.READ_ONLY -> !isWritable
      }
    }
  }
}

class ReaderModeEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project
    if (project == null || !instance(project).enabled) return
    if (editor !is EditorImpl) return

    applyReaderMode(project, editor, FileDocumentManager.getInstance().getFile(editor.document))
  }
}