// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.interaction

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.CapturedPointInfo
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.LightweightHint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object CombinedInlayInteractionHandler : InlayInteractionHandler {
  private val INTERACTION_HANDLER_EP = ExtensionPointName<InlayInteractionHandler>("com.intellij.codeInsight.inlayInteractionHandler")

  override fun handleLeftClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
    controlDown: Boolean,
  ): Boolean {
    for (handler in INTERACTION_HANDLER_EP.extensions) {
      if (handler.handleLeftClick(inlay, clickInfo, e, controlDown)) return true
    }
    return false
  }

  override fun handleRightClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  ): Boolean {
    for (handler in INTERACTION_HANDLER_EP.extensions) {
      if (handler.handleRightClick(inlay, clickInfo, e)) return true
    }
    return false
  }


  override fun handleHover(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  ): LightweightHint? {
    for (handler in INTERACTION_HANDLER_EP.extensions) {
      val hint = handler.handleHover(inlay, clickInfo, e)
      if (hint != null) return hint
    }
    return null
  }
}
