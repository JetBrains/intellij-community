// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl

import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorStateLevel
import com.intellij.openapi.fileEditor.impl.text.TextEditorState

data class DiffRequestProcessorEditorState(
  val embeddedEditorStates: List<TextEditorState>
) : FileEditorState {
  override fun canBeMergedWith(otherState: FileEditorState, level: FileEditorStateLevel): Boolean =
    otherState is DiffRequestProcessorEditorState &&
    embeddedEditorStates.zip(otherState.embeddedEditorStates).all { (l, r) -> l.canBeMergedWith(r, level) }

  override fun toString(): String = embeddedEditorStates.joinToString()
}
