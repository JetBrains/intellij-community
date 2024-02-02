// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined

import com.intellij.diff.editor.DiffEditorBase
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.FileEditorWithTextEditors
import com.intellij.openapi.fileEditor.impl.text.TextEditorState
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import javax.swing.JComponent

class CombinedDiffEditor(file: CombinedDiffVirtualFile, val factory: CombinedDiffComponentFactory) :
  DiffEditorBase(file, factory.getMainComponent(), factory.ourDisposable), FileEditorWithTextEditors {

  override fun dispose() {
    Disposer.dispose(factory.ourDisposable)
    super.dispose()
  }

  override fun getState(level: FileEditorStateLevel): FileEditorState {
    if (!Registry.`is`(DIFF_IN_NAVIGATION_HISTORY_KEY)) return FileEditorState.INSTANCE

    return factory.getState(level)
  }

  override fun setState(state: FileEditorState) {
    if (state !is CombinedDiffEditorState) return

    factory.setState(state)
  }

  override fun getPreferredFocusedComponent(): JComponent? = factory.getPreferredFocusedComponent()

  override fun getEmbeddedEditors(): List<Editor> {
    return factory.model.context.getUserData(COMBINED_DIFF_VIEWER_KEY)?.editors.orEmpty()
  }
}

data class CombinedDiffEditorState(
  val currentBlockIds: Set<CombinedBlockId>,
  val activeBlockId: CombinedBlockId,
  val activeEditorStates: List<TextEditorState>
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is CombinedDiffEditorState &&
           (currentBlockIds != otherState.currentBlockIds ||
            (activeBlockId == otherState.activeBlockId &&
             activeEditorStates.zip(otherState.activeEditorStates).all { (l, r) -> l.canBeMergedWith(r, level) } ))
  }
}
