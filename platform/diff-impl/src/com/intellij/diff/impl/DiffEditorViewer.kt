// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.DiffContext
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.vfs.VirtualFile
import java.util.EventListener
import javax.swing.JComponent

interface DiffEditorViewer {
  val disposable: CheckedDisposable
  val context: DiffContext

  val component: JComponent
  val preferredFocusedComponent: JComponent?

  val filesToRefresh: List<VirtualFile> get() = emptyList()
  val embeddedEditors: List<Editor> get() = emptyList()

  fun fireProcessorActivated()

  fun addListener(listener: DiffEditorViewerListener, disposable: Disposable?)

  fun setToolbarVerticalSizeReferent(component: JComponent)

  fun getState(level: FileEditorStateLevel): FileEditorState = FileEditorState.INSTANCE
  fun setState(state: FileEditorState) {}
}

interface DiffEditorViewerListener : EventListener {
  /**
   * Used to notify that tab name was changed
   */
  fun onActiveFileChanged() = Unit
}
