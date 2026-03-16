// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.ui.withTranslated
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Rectangle

/**
 * NB: You are responsible for triggering inlay updates when the presentation changes due to interactions, such as mouse hovering.
 *
 * Example:
 * ```
 * val presentation: InlayPresentation = ...
 * val inlay = inlayModel.addInlineElement(..., PresentationRenderer(presentation)) ?: ...
 * inlay.renderer.presentation.addListener(InlayContentListener(inlay))
 * ```
 * @see com.intellij.codeInsight.hints.presentation.InlayPresentation.addListener
 * @see com.intellij.codeInsight.hints.presentation.PresentationListener
 * @see com.intellij.codeInsight.hints.InlayContentListener
 */
class PresentationRenderer(val presentation: InlayPresentation) : EditorCustomElementRenderer, InputHandler by presentation {
  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    g as Graphics2D
    g.withTranslated(targetRegion.x, targetRegion.y) {
      presentation.paint(g, LinearOrderInlayRenderer.effectsIn(textAttributes))
    }
  }


  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    return presentation.width
  }

  // this should not be shown anywhere
  override fun getContextMenuGroupId(inlay: Inlay<*>): String {
    return "DummyActionGroup"
  }

  override fun toString(): String {
    return presentation.toString()
  }
}