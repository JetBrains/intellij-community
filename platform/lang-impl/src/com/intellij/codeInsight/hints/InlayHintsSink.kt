// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.psi.PsiElement

interface InlayHintsSink<T : EditorCustomElementRenderer> {
  /**
   * Add inlay to underlying editor.
   */
  fun addInlay(offset: Int, relatesToPrecedingText: Boolean, renderer: T)
}

fun InlayHintsSink<PresentationRenderer>.addInlineElement(
  element: PsiElement,
  presentation: InlayPresentation,
  isBeforeElement: Boolean = true
) {
  val offset = if (isBeforeElement) element.textOffset else element.textRange.endOffset
  addInlay(offset, false, PresentationRenderer(presentation))
}