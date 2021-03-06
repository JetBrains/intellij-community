// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.presentation.InlayPresentation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.awt.Color
import java.awt.Point
import java.awt.event.MouseEvent
import javax.swing.Icon


@ApiStatus.Experimental
interface InlayPresentationFactory {
  /**
   * Text that can be used for elements that ARE valid syntax if they are pasted into file.
   */
  @Contract(pure = true)
  fun text(text: String) : InlayPresentation

  /**
   * Small text should be used for elements which text is not a valid syntax of the file, where this inlay is inserted.
   * Should be used with [container] to be aligned and properly placed
   * @return presentation that renders small text
   */
  @Contract(pure = true)
  fun smallText(text: String) : InlayPresentation

  /**
   * @return presentation that wraps existing with borders, background and rounded corners if set
   * @param padding properties of space between [presentation] and border that is filled with background and has corners
   * @param roundedCorners properties of rounded corners. If null, corners will have right angle
   * @param background color of background, if null, no background will be rendered
   * @param backgroundAlpha value from 0 to 1 of background opacity
   */
  @Contract(pure = true)
  fun container(
    presentation: InlayPresentation,
    padding: Padding? = null,
    roundedCorners: RoundedCorners? = null,
    background: Color? = null,
    backgroundAlpha: Float = 0.55f
  ) : InlayPresentation

  /**
   * @return presentation that renders icon
   */
  @Contract(pure = true)
  fun icon(icon: Icon) : InlayPresentation

  /**
   * @return presentation with given mouse handlers
   */
  @Contract(pure = true)
  fun mouseHandling(base: InlayPresentation,
                    clickListener: ClickListener?,
                    hoverListener: HoverListener?) : InlayPresentation

  interface HoverListener {
    fun onHover(event: MouseEvent, translated: Point)
    fun onHoverFinished()
  }

  fun interface ClickListener {
    fun onClick(event: MouseEvent, translated: Point)
  }


  data class Padding(
    val left: Int,
    val right: Int,
    val top: Int,
    val bottom: Int
  )

  data class RoundedCorners(
    val arcWidth: Int,
    val arcHeight: Int
  )

  @Contract(pure = true)
  fun smallScaledIcon(icon: Icon): InlayPresentation
}
