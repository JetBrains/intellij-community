// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.isRhizomeAdEnabled
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener

internal class BackendEditorFactoryListener : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    if (!isRhizomeAdEnabled) return
    ApplicationManager.getApplication().service<BackendEditors>().editorCreated(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    if (!isRhizomeAdEnabled) return
    ApplicationManager.getApplication().service<BackendEditors>().editorReleased(event.editor)
  }
}
