// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.interaction

import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayActionService
import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.CapturedPointInfo
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.ui.LightweightHint
import com.intellij.ui.awt.RelativePoint
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DefaultInlayInteractionHandler : InlayInteractionHandler {
  override fun handleLeftClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
    controlDown: Boolean,
  ) {
    if (clickInfo.entry == null) return
    val entry = clickInfo.entry
    val presentationList = clickInfo.presentationList
    val editor = e.editor
    val project = editor.project
    val clickArea = clickInfo.entry.clickArea
    if (clickArea != null && project != null) {
      val actionData = clickArea.actionData
      if (controlDown) {
        service<DeclarativeInlayActionService>().invokeActionHandler(actionData, e)
      }
    }
    if (entry.parentIndexToSwitch != (-1).toByte()) {
      presentationList.toggleTreeState(entry.parentIndexToSwitch)
      inlay.renderer.invalidate()
    }
  }

  override fun handleHover(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  ): LightweightHint? {
    val tooltip = clickInfo.presentationList.model.tooltip ?: return null
    return PresentationFactory(e.editor).showTooltip(e.mouseEvent, tooltip)

  }

  override fun handleRightClick(
    inlay: Inlay<out DeclarativeInlayRendererBase<*>>,
    clickInfo: CapturedPointInfo,
    e: EditorMouseEvent,
  ) {
    val inlayData = clickInfo.presentationList.model
    service<DeclarativeInlayActionService>().invokeInlayMenu(inlayData, e, RelativePoint(e.mouseEvent.locationOnScreen))
  }

}
