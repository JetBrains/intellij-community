// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.editor.backend

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.impl.ad.isRhizomeAdRebornEnabled

internal class BackendEditorFactoryListener : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    if (!isRhizomeAdRebornEnabled) return
    // TODO: disable due to `com.intellij.diagnostic.PluginException: Cannot find service com.intellij.platform.editor.backend.BackendEditors`
    //ApplicationManager.getApplication().service<BackendEditors>().editorCreated(event.editor)
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    if (!isRhizomeAdRebornEnabled) return
    // TODO: disable due to `com.intellij.diagnostic.PluginException: Cannot find service com.intellij.platform.editor.backend.BackendEditors`
    //ApplicationManager.getApplication().service<BackendEditors>().editorReleased(event.editor)
  }
}
