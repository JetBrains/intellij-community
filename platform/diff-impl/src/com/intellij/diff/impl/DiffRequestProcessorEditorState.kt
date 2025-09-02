// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.diff.editor.DiffEditorTabFilesUtil
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.fileEditor.impl.FileEditorStateWithPreferredOpenMode
import com.intellij.openapi.fileEditor.impl.text.TextEditorState

internal data class DiffRequestProcessorEditorState(
  @JvmField val embeddedEditorStates: List<TextEditorState>
) : FileEditorStateWithPreferredOpenMode {
  override val openMode: FileEditorManagerImpl.OpenMode?
    get() = if (!DiffEditorTabFilesUtil.isDiffInEditor) FileEditorManagerImpl.OpenMode.NEW_WINDOW else null

  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean {
    return otherState is DiffRequestProcessorEditorState &&
           embeddedEditorStates.zip(otherState.embeddedEditorStates).all { (l, r) -> l.canBeMergedWith(r, level) }
  }

  override fun toString(): String = embeddedEditorStates.joinToString()
}
