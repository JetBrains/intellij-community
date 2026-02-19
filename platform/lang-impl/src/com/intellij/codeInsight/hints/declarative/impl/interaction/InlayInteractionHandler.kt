// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.interaction

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.CapturedPointInfo
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface InlayInteractionHandler {
  fun handleLeftClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
    controlDown: Boolean,
  )

  fun handleHover(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  ): LightweightHint?

  fun handleRightClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  )
}
