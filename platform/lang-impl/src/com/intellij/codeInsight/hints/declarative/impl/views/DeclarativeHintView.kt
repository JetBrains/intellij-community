// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayMouseArea
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
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
interface DeclarativeHintView {
  @RequiresEdt
  fun updateModel(newModel: InlayData)

  @RequiresEdt
  fun calcWidthInPixels(inlay: Inlay<*>, fontMetricsStorage: InlayTextMetricsStorage): Int

  fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
    fontMetricsStorage: InlayTextMetricsStorage,
  )

  fun handleLeftClick(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
    controlDown: Boolean,
  )

  fun handleHover(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  ): LightweightHint?

  fun handleRightClick(
    e: EditorMouseEvent,
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  )

  fun getMouseArea(
    pointInsideInlay: Point,
    fontMetricsStorage: InlayTextMetricsStorage,
  ): InlayMouseArea?
}