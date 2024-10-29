// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.registry.Registry

private class MinimapEditorFactoryListener : EditorFactoryListener {
  override fun editorCreated(event: EditorFactoryEvent) {
    if (Registry.`is`("editor.minimap.enabled")) {
      MinimapService.getInstance().editorOpened(event.editor)
    }
  }
}