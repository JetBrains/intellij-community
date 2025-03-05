// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend

import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import kotlinx.coroutines.CoroutineScope

internal class BackendEditors(private val cs: CoroutineScope) {

  init {
    check(isRhizomeAdEnabled)
  }

  fun editorCreated(editor: Editor) {
    val editorState = BackendEditorState(cs, editor)
    editor.putUserData(STATE_KEY, editorState)
  }

  fun editorReleased(editor: Editor) {
    editor.getUserData(STATE_KEY)
      ?.release()
  }
}

private val STATE_KEY: Key<BackendEditorState> = Key.create("BackendEditorState")
