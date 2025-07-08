// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationEntry
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.EditorMouseEvent
import java.awt.event.MouseEvent

fun InlayPresentationEntry.simulateClick(editor: Editor, presentationList: InlayPresentationList) {
  this.handleClick(dummyEditorMouseEvent(editor), presentationList, true)
}

private fun dummyEditorMouseEvent(editor: Editor): EditorMouseEvent {
  val mouseEvent = MouseEvent(editor.getContentComponent(), 0, 0, 0, 0, 0, 0, false, 0)
  return EditorMouseEvent(editor, mouseEvent, editor.getMouseEventArea(mouseEvent))
}