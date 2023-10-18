package com.intellij.codeInsight.hints.presentation

import com.intellij.codeInsight.hints.InlayPresentationFactory
import org.jetbrains.annotations.ApiStatus
import java.awt.Point
import java.awt.event.MouseEvent

@ApiStatus.Internal
class OnPressedPresentation(
  presentation: InlayPresentation,
  private val pressedListener: InlayPresentationFactory.ClickListener
) : StaticDelegatePresentation(presentation) {
  constructor(presentation: InlayPresentation, listener: (MouseEvent, Point) -> Unit) : this(
    presentation,
    InlayPresentationFactory.ClickListener { event, translated -> listener(event, translated) })

  override fun mousePressed(event: MouseEvent, translated: Point) {
    super.mousePressed(event, translated)
    pressedListener.onClick(event, translated)
  }
}