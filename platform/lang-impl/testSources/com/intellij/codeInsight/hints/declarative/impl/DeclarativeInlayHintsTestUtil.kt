// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.CapturedPointInfo
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationEntry
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import java.awt.event.MouseEvent

fun InlayPresentationEntry.simulateClick(
  inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
  presentationList: InlayPresentationList,
) {
  inlay.renderer.getInteractionHandler().handleLeftClick(
    inlay,
    CapturedPointInfo(presentationList, this),
    dummyEditorMouseEvent(inlay.editor),
    false,
  )
}

private fun dummyEditorMouseEvent(editor: Editor): EditorMouseEvent {
  val mouseEvent = MouseEvent(editor.getContentComponent(), 0, 0, 0, 0, 0, 0, false, 0)
  return EditorMouseEvent(editor, mouseEvent, editor.getMouseEventArea(mouseEvent))
}