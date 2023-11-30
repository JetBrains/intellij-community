// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.presentation

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import java.awt.Cursor
import java.awt.Point
import java.awt.event.MouseEvent

class WithCursorOnHoverPresentation(
  presentation: InlayPresentation,
  val cursor: Cursor,
  private val editor: Editor) : StaticDelegatePresentation(presentation) {
  private var onHoverPredicate: (MouseEvent) -> Boolean = { true }

  constructor(presentation: InlayPresentation, cursor: Cursor, editor: Editor, onHoverPredicate: (MouseEvent) -> Boolean) : this(
    presentation, cursor, editor) {
    this.onHoverPredicate = onHoverPredicate
  }

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    super.mouseMoved(event, translated)
    (editor as? EditorImpl)?.setCustomCursor(this::class, if (onHoverPredicate(event)) cursor else null)
  }

  override fun mouseExited() {
    super.mouseExited()
    (editor as? EditorImpl)?.setCustomCursor(this::class, null)
  }
}