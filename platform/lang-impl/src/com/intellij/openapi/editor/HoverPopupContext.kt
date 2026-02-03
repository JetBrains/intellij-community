// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.documentation.ide.impl.calcTargetDocumentationInfo
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.psi.PsiElement
import com.intellij.reference.SoftReference
import java.awt.Point
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import kotlin.math.max


internal class HoverPopupContext(
  private val startTimestamp: Long,
  private val targetOffset: Int,
  private val highlightInfo: Reference<HighlightInfo>?,
  private val elementForQuickDoc: Reference<PsiElement>?,
  private val showImmediately: Boolean,
  private val showDocumentation: Boolean,
  private val keepPopupOnMouseMove: Boolean,
) {

  enum class Relation {
    SAME,  // no need to update popup
    SIMILAR,  // popup needs to be updated
    DIFFERENT, // popup needs to be closed, and the new one shown
  }

  constructor(
    startTimestamp: Long,
    targetOffset: Int,
    highlightInfo: HighlightInfo?,
    elementForQuickDoc: PsiElement?,
    showImmediately: Boolean,
    showDocumentation: Boolean,
    keepPopupOnMouseMove: Boolean,
  ) : this(
    startTimestamp,
    targetOffset,
    highlightInfo?.let { WeakReference(it) },
    elementForQuickDoc?.let { WeakReference(it) },
    showImmediately,
    showDocumentation,
    keepPopupOnMouseMove,
  )

  fun compareTo(other: HoverPopupContext?): Relation {
    if (other == null) {
      return Relation.DIFFERENT
    }
    val highlightInfo = getHighlightInfo()
    if (highlightInfo != other.getHighlightInfo()) {
      return Relation.DIFFERENT
    }
    if (getElementForQuickDoc() == other.getElementForQuickDoc()) {
      return Relation.SAME
    }
    return if (highlightInfo == null) Relation.DIFFERENT else Relation.SIMILAR
  }

  fun keepPopupOnMouseMove(): Boolean {
    return keepPopupOnMouseMove
  }

  fun getHighlightInfo(): HighlightInfo? {
    return SoftReference.dereference(highlightInfo)
  }

  fun getShowingDelay(): Long {
    if (showImmediately) {
      return 0
    }
    return max(0, EditorSettingsExternalizable.getInstance().tooltipsDelay - (System.currentTimeMillis() - startTimestamp))
  }

  fun getPopupPosition(editor: Editor): VisualPosition {
    val highlightInfo = getHighlightInfo()
    if (highlightInfo == null) {
      return HoverDocPopupLocationProvider.getInstance().getPopupPosition(targetOffset, getElementForQuickDoc(), editor)
    }
    val targetPosition = editor.offsetToVisualPosition(targetOffset)
    val endPosition = editor.offsetToVisualPosition(highlightInfo.getEndOffset())
    if (endPosition.line <= targetPosition.line) {
      return targetPosition
    }
    val targetPoint = editor.visualPositionToXY(targetPosition)
    val endPoint = editor.visualPositionToXY(endPosition)
    val targetY = if (endPoint.x > targetPoint.x) endPoint.y else editor.visualLineToY(endPosition.line - 1)
    val resultPoint = Point(targetPoint.x, targetY)
    return editor.xyToVisualPosition(resultPoint)
  }

  fun calcInfo(editor: Editor): EditorHoverInfo? {
    val highlightHoverInfo = HighlightHoverInfo.highlightHoverInfo(editor, getHighlightInfo())
    val documentationHoverInfo = documentationHoverInfo(editor)
    if (highlightHoverInfo == null && documentationHoverInfo == null) {
      return null
    }
    return EditorHoverInfo(highlightHoverInfo, documentationHoverInfo)
  }

  private fun documentationHoverInfo(editor: Editor): DocumentationHoverInfo? {
    if (showDocumentation && EditorSettingsExternalizable.getInstance().isShowQuickDocOnMouseOverElement) {
      try {
        return calcTargetDocumentationInfo(editor.getProject()!!, editor, targetOffset)
      } catch (_: IndexNotReadyException) {
        // IDEA-277609 [documentation] swallow `IndexNotReadyException`
      }
    }
    return null
  }

  private fun getElementForQuickDoc(): PsiElement? {
    return SoftReference.dereference(elementForQuickDoc)
  }
}
