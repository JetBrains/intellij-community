// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.inlayRenderer

import com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.InlayPosition
import com.intellij.codeInsight.hints.declarative.InlineInlayPosition
import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.DeclarativeHintView
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.ui.LightweightHint
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

@ApiStatus.Internal
abstract class DeclarativeInlayRendererBase<Model>(
  val providerId: String,
  val sourceId: String,
  val fontMetricsStorage: InlayTextMetricsStorage,
) : EditorCustomElementRenderer {
  lateinit var inlay: Inlay<out DeclarativeInlayRendererBase<Model>> private set

  open fun initInlay(inlay: Inlay<out DeclarativeInlayRendererBase<Model>>) {
    this.inlay = inlay
  }

  internal abstract val view: DeclarativeHintView<Model>

  abstract val presentationLists: List<InlayPresentationList>

  @RequiresEdt
  @ApiStatus.Internal
  fun updateModel(newModel: Model) {
    view.updateModel(newModel)
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return view.calcWidthInPixels(inlay, fontMetricsStorage)
  }

  override fun paint(inlay: Inlay<*>, g: Graphics2D, targetRegion: Rectangle2D, textAttributes: TextAttributes) {
    view.paint(inlay, g, targetRegion, textAttributes, fontMetricsStorage)
  }

  internal fun handleLeftClick(e: EditorMouseEvent, pointInsideInlay: Point, controlDown: Boolean) {
    view.handleLeftClick(e, pointInsideInlay, fontMetricsStorage, controlDown)
  }

  @ApiStatus.Internal
  fun handleHover(e: EditorMouseEvent, pointInsideInlay: Point): LightweightHint? {
    return view.handleHover(e, pointInsideInlay, fontMetricsStorage)
  }

  internal fun handleRightClick(e: EditorMouseEvent, pointInsideInlay: Point) {
    return view.handleRightClick(e, pointInsideInlay, fontMetricsStorage)
  }

  internal fun getMouseArea(pointInsideInlay: Point): InlayMouseArea? {
    return view.getMouseArea(pointInsideInlay, fontMetricsStorage)
  }

  // this should not be shown anywhere, but it is required to show custom menu in com.intellij.openapi.editor.impl.EditorImpl.DefaultPopupHandler.getActionGroup
  override fun getContextMenuGroupId(inlay: Inlay<*>): String {
    return "DummyActionGroup"
  }

  @ApiStatus.Internal
  fun toInlayData(needUpToDateOffsets: Boolean = true): List<InlayData> {
    // this.inlay should always be initialized right after construction.
    // However, InlayModel.Listener.onAdded will be called before that can happen,
    // and someone (e.g., rem-dev backend) might want to serialize right away;
    // it is not a problem, though, because at that moment,
    // the offsets of the original declared position are still actual.
    return if (needUpToDateOffsets && this::inlay.isInitialized) {
      presentationLists.map { it.model.copyAndUpdatePosition(inlay) }
    }
    else {
      presentationLists.map { it.model }
    }
  }
}

private fun InlayPosition.copyAndUpdateOffset(inlay: Inlay<*>): InlayPosition {
  // important to store position based on the inlay offset, not the renderer one
  // the latter does not receive updates from the inlay model when the document is changed
  val newOffset = inlay.offset
  return when (this) {
    is AboveLineIndentedPosition -> AboveLineIndentedPosition(newOffset, this.verticalPriority, this.priority)
    is EndOfLinePosition -> EndOfLinePosition(inlay.editor.document.getLineNumber(newOffset))
    is InlineInlayPosition -> InlineInlayPosition(newOffset, this.relatedToPrevious, this.priority)
  }
}

private fun InlayData.copyAndUpdatePosition(inlay: Inlay<*>): InlayData = copy(position = position.copyAndUpdateOffset(inlay))