// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.InlayPresentationFactory.*
import com.intellij.codeInsight.hints.presentation.PresentationFactory
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics
import java.awt.Point
import java.awt.Rectangle
import java.awt.event.MouseEvent

@ApiStatus.Internal
enum class BlockInlayIndentStrategy {
  ATTACH_TO_FIRST_NON_BLANK_CHARACTER,
  INDENT_SPECIFIED_CHARACTERS_NUMBER
}

@ApiStatus.Internal
interface BlockInlayPaddingProvider {
  fun updateHorizontalPadding(inlay: Inlay<*>, currentPadding: Padding): Padding
  fun updateVerticalPadding(inlay: Inlay<*>, currentPadding: Padding): Padding
}

@ApiStatus.Internal
class BlockInlayWithIndentPaddingProvider(
  private val editor: Editor,
  private val strategy: BlockInlayIndentStrategy,
  private val indentSpecifiedInCharactersNumber: Int,
  private val extraLinesNumberPlaceholderAbove: Int,
  private val extraLinesNumberPlaceholderBelow: Int,
) : BlockInlayPaddingProvider {
  companion object {
    private val LOG = logger<BlockInlayWithIndentPaddingProvider>()
  }

  override fun updateHorizontalPadding(inlay: Inlay<*>, currentPadding: Padding): Padding = currentPadding.copy(left = getIndent(inlay))

  override fun updateVerticalPadding(inlay: Inlay<*>, currentPadding: Padding): Padding = currentPadding.copy(
    top = extraLinesNumberPlaceholderAbove * editor.lineHeight,
    bottom = extraLinesNumberPlaceholderBelow * editor.lineHeight)

  private fun getIndent(inlay: Inlay<*>): Int {
    try {
      when (strategy) {
        BlockInlayIndentStrategy.ATTACH_TO_FIRST_NON_BLANK_CHARACTER -> {
          val lineStartOffset = DocumentUtil.getLineStartOffset(inlay.offset, editor.document)
          val lineEndOffset = DocumentUtil.getLineEndOffset(inlay.offset, editor.document)
          val shiftForward = CharArrayUtil.shiftForward(editor.document.immutableCharSequence, lineStartOffset, " \t")
          return editor.offsetToXY(if (shiftForward == lineEndOffset) lineStartOffset else shiftForward).x
        }
        BlockInlayIndentStrategy.INDENT_SPECIFIED_CHARACTERS_NUMBER -> {
          val pos = VisualPosition(editor.document.getLineNumber(inlay.offset), indentSpecifiedInCharactersNumber)
          return editor.visualPositionToXY(pos).x
        }
      }
    }
    catch (ex: Exception) {
      LOG.trace("Failed to calculate an indent for a block inlay: ${inlay.renderer}. Returning zero indent.")
      LOG.trace(ex)
    }

    return 0
  }
}

/**
 * Updates padding during every inlay update.
  */
@ApiStatus.Internal
open class BlockInlayWithPaddingRenderer(
  private val paddingProvider: BlockInlayPaddingProvider,
  factory: PresentationFactory, constrainedPresentations: Collection<ConstrainedPresentation<*, BlockConstraints>>,
) : BlockInlayRenderer(factory, constrainedPresentations) {

  lateinit var inlay: Inlay<*>

  fun initialize(inlay: Inlay<*>) {
    assert(!::inlay.isInitialized) { "Inlay already defined for current renderer" }
    this.inlay = inlay
    inlay.update()
  }

  var padding: Padding = Padding(0, 0, 0, 0)
    private set

  override fun calcHeightInPixels(inlay: Inlay<*>): Int {
    padding = paddingProvider.updateVerticalPadding(inlay, padding)
    return padding.top + super.calcHeightInPixels(inlay) + padding.bottom
  }

  override fun calcWidthInPixels(inlay: Inlay<*>): Int {
    padding = paddingProvider.updateHorizontalPadding(inlay, padding)
    return padding.left + super.calcWidthInPixels(inlay) + padding.right
  }

  private fun applyPadding(targetRegion: Rectangle): Rectangle {
    val translated = Point(targetRegion.location.x + padding.left, targetRegion.location.y + padding.top)
    return Rectangle(translated.x, translated.y,
                     targetRegion.width - (padding.left + padding.right),
                     targetRegion.height - (padding.top + padding.bottom))
  }

  override fun paint(inlay: Inlay<*>, g: Graphics, targetRegion: Rectangle, textAttributes: TextAttributes) {
    val region = applyPadding(targetRegion)
    super.paint(inlay, g, region, textAttributes)
  }

  private fun isInsideActiveArea(translated: Point): Boolean {
    if (!::inlay.isInitialized)
      return false

    val bounds = inlay.bounds ?: return false
    val activeWidth = bounds.width - (padding.left + padding.right)
    val activeHeight = bounds.height - (padding.top + padding.bottom)
    val translatedToActiveArea = Point(translated.x - padding.left, translated.y - padding.top)

    return translatedToActiveArea.x in 0..activeWidth
           && translatedToActiveArea.y in 0..activeHeight
  }

  override fun mouseClicked(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mouseClicked(event, translated)
  }

  override fun mousePressed(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mousePressed(event, translated)
  }

  override fun mouseReleased(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated))
      super.mouseReleased(event, translated)
  }

  private var isMouseOverActiveArea: Boolean = false

  override fun mouseMoved(event: MouseEvent, translated: Point) {
    if (isInsideActiveArea(translated)) {
      isMouseOverActiveArea = true
      super.mouseMoved(event, translated)
    }
    else {
      val wasOver = isMouseOverActiveArea
      isMouseOverActiveArea = false

      if (wasOver)
        super.mouseExited()
    }
  }

  override fun mouseExited() { /* handling in mouseMoved */
  }
}