// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.editor

import com.intellij.diff.impl.DiffEditorViewer
import com.intellij.diff.impl.DiffEditorViewerListener
import com.intellij.diff.impl.DiffRequestProcessor
import com.intellij.diff.impl.DiffSettingsHolder
import com.intellij.diff.util.DiffUserDataKeysEx
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.reopenVirtualFileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.JComponent

@Suppress("LeakingThis")
open class DiffEditorViewerFileEditor(
  file: VirtualFile,
  val editorViewer: DiffEditorViewer,
) : DiffFileEditorBase(file,
                       editorViewer.component,
                       editorViewer.disposable), FileEditorWithTextEditors {
  private val settings by lazy { DiffSettingsHolder.DiffSettings.getSettings() }

  init {
    editorViewer.addListener(MyEditorViewerListener(), this)
  }

  override fun dispose() {
    val explicitDisposable = editorViewer.context.getUserData(DiffUserDataKeysEx.DIFF_IN_EDITOR_WITH_EXPLICIT_DISPOSABLE)
    if (explicitDisposable != null) {
      explicitDisposable.run()
    }
    else {
      Disposer.dispose(editorViewer.disposable)
    }
    super.dispose()
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (settings.isIncludedInNavigationHistory == DiffSettingsHolder.IncludeInNavigationHistory.Never) {
      return FileEditorState.INSTANCE
    }

    return editorViewer.getState(level)
  }

  override fun setState(state: FileEditorState) {
    if (settings.isIncludedInNavigationHistory == DiffSettingsHolder.IncludeInNavigationHistory.Never) {
      return
    }

    editorViewer.setState(state)
  }

  override fun getPreferredFocusedComponent(): JComponent? = editorViewer.preferredFocusedComponent

  override fun selectNotify() {
    editorViewer.fireProcessorActivated()
  }

  override fun getFilesToRefresh(): List<VirtualFile> = editorViewer.filesToRefresh
  override fun getEmbeddedEditors(): List<Editor> = editorViewer.embeddedEditors

  private inner class MyEditorViewerListener : DiffEditorViewerListener {
    override fun onActiveFileChanged() {
      val project = editorViewer.context.project ?: return
      FileEditorManagerEx.getInstanceEx(project).updateFilePresentation(file)
    }
  }

  companion object {
    fun reloadDiffEditorsForFiles(project: Project, condition: (VirtualFile) -> Boolean) {
      val editorManager = FileEditorManager.getInstance(project)
      val diffFiles = editorManager.allEditors
        .filter { it is DiffEditorViewerFileEditor }
        .mapNotNull { it.file }
        .filter(condition)
        .toSet()

      for (file in diffFiles) {
        reopenVirtualFileEditor(project, file, file)
      }
    }
  }
}

@Deprecated("Use DiffEditorViewerFileEditors instead", replaceWith = ReplaceWith("DiffEditorViewerFileEditor"))
open class DiffRequestProcessorEditor(
  file: VirtualFile,
  @Deprecated("use editorViewer instead", replaceWith = ReplaceWith("editorViewer"))
  val processor: DiffRequestProcessor,
) : DiffEditorViewerFileEditor(file, processor)
