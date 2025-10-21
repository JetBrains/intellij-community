// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.views

import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Point
import java.awt.geom.Rectangle2D

/**
 * There are 2-3 levels to declarative inlay presentation:
 * 1. [InlayPresentationEntry] represents a leaf of the [inlay tree][com.intellij.codeInsight.hints.declarative.impl.InlayData.tree].
 *  One entry usually corresponds to a [PresentationTreeBuilder.text][com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder.text]
 *  or [icon][com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder.icon] call.
 * 2. [InlayPresentationList] represents a subsequence of the leaves of an [inlay tree][com.intellij.codeInsight.hints.declarative.impl.InlayData.tree].
 *   If there are no [collapsibleList][com.intellij.codeInsight.hints.declarative.PresentationTreeBuilder.collapsibleList] branches in the tree,
 *   then it is a sequence of all the leaves.
 *   Corresponds to a single [com.intellij.codeInsight.hints.declarative.InlayTreeSink.addPresentation] call.
 * 3. Optional. [InlayPresentationComposite] is used when multiple declarative inlays need to be placed in a single editor inlay,
 *   e.g.; multiple [above-line][com.intellij.codeInsight.hints.declarative.AboveLineIndentedPosition] inlays rendered on a single
 *   [block inlay element][com.intellij.openapi.editor.InlayModel.addBlockElement]
 */
@ApiStatus.Internal
interface InlayTopLevelElement<Model> : Invalidable, InlayElementWithMargins<InlayTextMetricsStorage> {
  @RequiresEdt
  fun paint(
    inlay: Inlay<*>,
    g: Graphics2D,
    targetRegion: Rectangle2D,
    textAttributes: TextAttributes,
    textMetricsStorage: InlayTextMetricsStorage,
  )

  @RequiresEdt
  fun findEntryAtPoint(pointInsideInlay: Point, textMetricsStorage: InlayTextMetricsStorage): CapturedPointInfo?

  @RequiresEdt
  fun updateModel(newModel: Model)
}

@ApiStatus.Internal
interface Invalidable {
  @RequiresEdt
  fun invalidate()
}

@ApiStatus.Internal
data class CapturedPointInfo(val presentationList: InlayPresentationList, val entry: InlayPresentationEntry?)