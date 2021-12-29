// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.project.Project


class VisualFormattingLayerNewEditorListener(val project: Project) : EditorFactoryListener {

  override fun editorCreated(event: EditorFactoryEvent) {
    val editor: Editor = event.editor
    if (project == editor.project) {
      editor.addVisualLayer()
    }
  }

  override fun editorReleased(event: EditorFactoryEvent) {
    val editor: Editor = event.editor
    if (project == editor.project) {
      editor.removeVisualLayer()
    }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other == null || javaClass != other.javaClass) return false
    val that = other as VisualFormattingLayerNewEditorListener
    return project == that.project
  }

  override fun hashCode(): Int {
    return project.hashCode()
  }

}
