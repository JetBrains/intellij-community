// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.visualLayer

import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project


class VisualFormattingLayerDocumentListener(val project: Project) : DocumentListener {

  override fun documentChanged(event: DocumentEvent) {
    EditorFactory.getInstance()
      .getEditors(event.document, project)
      .forEach { editor ->
        editor.removeVisualFormattingElements()
        editor.addVisualFormattingElements()
      }
  }

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    other as VisualFormattingLayerDocumentListener
    return project == other.project
  }

  override fun hashCode(): Int {
    return project.hashCode()
  }

}
