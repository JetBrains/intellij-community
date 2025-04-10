// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

import com.intellij.codeInsight.inline.completion.render.InlineCompletionVolumetricTextBlockFactory.Companion.accumulatedWidthToInt
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.SoftWrapModelImpl
import com.intellij.openapi.editor.impl.softwrap.SoftWrapDrawingType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.util.Key
import com.intellij.ui.ColorUtil
import java.awt.Font
import java.util.*

internal class InlineCompletionSoftWrapManager private constructor(private val editor: Editor) : Disposable {

  private val arrows by lazy { chooseArrows() }

  fun getSoftWrapModelIfEnabled(): SoftWrapModelImpl? {
    if (!isSoftWrappingEnabled()) {
      return null
    }
    return editor.softWrapModel as? SoftWrapModelImpl
  }

  /**
   * Formats [lines] to make them fit within editor lines.
   *
   * Return `null` if it's impossible to format.
   * If some line didn't change, it will be added to the result as the same instance of the list.
   *
   * **Invariant**: [RenderedLine.startX] may be non-zero only for the first line!
   *
   * @see [splitByAvailableWidth]
   */
  fun softWrap(
    lines: List<RenderedLine>,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory
  ): List<List<VolumetricInlineCompletionTextBlock>>? {
    val softWrapModel = getSoftWrapModelIfEnabled() ?: return null

    val linesToFix = LinkedList(lines)
    val resultLines = mutableListOf<List<VolumetricInlineCompletionTextBlock>>()
    val visibleAreaWidth = softWrapModel.applianceManager.widthProvider.visibleAreaWidth
    while (linesToFix.isNotEmpty()) {
      val line = linesToFix.first()
      val blocks = line.blocks
      if (blocks.isEmpty()) {
        resultLines.add(blocks)
        linesToFix.removeFirst()
        continue
      }

      val startX = line.startX
      val editorAvailableLineWidth = visibleAreaWidth - startX // TODO tune
      val (leftSplitPart, rightSplitPart) = splitByAvailableWidth(blocks, editorAvailableLineWidth, volumetricFactory)
      // TODO check that line is not empty
      if (rightSplitPart.isEmpty()) { // The line completely fits
        resultLines += leftSplitPart
        linesToFix.removeFirst()
        continue
      }
      if (leftSplitPart.isEmpty()) { // Not a single part of the line fits
        if (startX > 0) {
          // Let's try to put it on the next empty line
          resultLines.add(emptyList())
          linesToFix.removeFirst()
          linesToFix.addFirst(RenderedLine(blocks, 0))
        }
        else {
          // Idk what to do... Idk how to split... TODO
          resultLines.add(blocks)
          linesToFix.removeFirst()
        }
        continue
      }
      resultLines.add(leftSplitPart)
      linesToFix.removeFirst()
      linesToFix.addFirst(RenderedLine(rightSplitPart, 0)) // 0 because it's a block inlay, for sure
    }

    return resultLines
  }

  /**
   * Splits [blocks] into two parts based on the available width for the first part.
   *
   * Split may be done:
   * * By whitespace character. In this case, the whitespace block is visually removed and replaced with the only one split-space.
   * * TODO [not done] If there is no whitespace character we can split by, we split it by the first non-fitting character.
   */
  fun splitByAvailableWidth(
    blocks: List<VolumetricInlineCompletionTextBlock>,
    availableWidth: Int,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory,
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>> {
    var currentIndex = 0 // the first over-width block
    var currentWidth = 0.0
    for ((_, width) in blocks) {
      currentWidth += width
      if (accumulatedWidthToInt(currentWidth) <= availableWidth) {
        currentIndex++
      }
      else {
        break
      }
    }
    if (accumulatedWidthToInt(currentWidth) <= availableWidth) {
      return blocks to emptyList()
    }
    check(currentIndex < blocks.size)
    while (currentIndex >= 0) {
      currentWidth -= blocks[currentIndex].widthInPixels

      val block = blocks[currentIndex].block
      val text = block.text
      var currentCharIndex = text.length - 1
      while (currentCharIndex >= 0) {
        if (!text[currentCharIndex].isWhitespace()) {
          currentCharIndex--
          continue
        }
        var splitStart = currentCharIndex
        while (splitStart >= 0 && text[splitStart].isWhitespace()) {
          splitStart--
        }

        val left = text.substring(0, splitStart + 1)
        val leftBlocks = listOfNotNull(
          if (left.isNotEmpty()) InlineCompletionRenderTextBlock(left, block.attributes) else null,
          // TODO the arrow doesn't inherit background of the current caret row
          getSoftWrapArrow(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
        ).toVolumetric(volumetricFactory)

        val leftWidth = accumulatedWidthToInt(leftBlocks.sumOf { it.widthInPixels })
        if (accumulatedWidthToInt(currentWidth + leftWidth) <= availableWidth) {
          val right = text.substring(currentCharIndex + 1)
          val rightBlocks = listOfNotNull(
            getSoftWrapArrow(SoftWrapDrawingType.AFTER_SOFT_WRAP),
            if (right.isNotEmpty()) InlineCompletionRenderTextBlock(right, block.attributes) else null,
          ).toVolumetric(volumetricFactory)

          // TODO handle empty block
          val finalLeft = blocks.subList(0, currentIndex) + leftBlocks
          val finalRight = rightBlocks + blocks.subList(currentIndex + 1, blocks.size)
          return finalLeft to finalRight
        }

        currentCharIndex = splitStart
      }
      currentIndex--
    }

    // TODO add arrow to the left
    return emptyList<VolumetricInlineCompletionTextBlock>() to blocks
  }

  private fun getSoftWrapArrow(type: SoftWrapDrawingType): InlineCompletionRenderTextBlock? {
    val arrows = this.arrows ?: return null
    val text = when (type) {
      SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED -> arrows.first
      SoftWrapDrawingType.AFTER_SOFT_WRAP -> arrows.second
    }
    return InlineCompletionRenderTextBlock(text, getArrowAttributes())
  }

  override fun dispose() = Unit

  private fun isSoftWrappingEnabled(): Boolean {
    return editor.softWrapModel.isSoftWrappingEnabled && editor.softWrapModel is SoftWrapModelImpl
  }

  private fun chooseArrows(): Pair<String, String>? {
    return ARROW_CANDIDATES.find { canDisplay(it.first) && canDisplay(it.second) }?.let {
      it.first.toString() to it.second.toString()
    }
  }

  private fun canDisplay(char: Char): Boolean {
    val fontInfo = EditorUtil.fontForChar(char, Font.PLAIN, editor)
    return fontInfo.canDisplay(char.code)
  }

  private fun getArrowAttributes(): TextAttributes {
    val base = editor.colorsScheme.getAttributes(HighlighterColors.TEXT)?.clone() ?: TextAttributes()
    val color = base.foregroundColor ?: return base
    base.foregroundColor = ColorUtil.withAlpha(color, (color.alpha / 255.0) * 0.8)
    return base
  }

  class RenderedLine(val blocks: List<VolumetricInlineCompletionTextBlock>, val startX: Int)

  companion object : InlineCompletionComponentFactory<InlineCompletionSoftWrapManager>() {
    // Reused from `CompositeSoftWrapPainter.java`
    private val ARROW_CANDIDATES = listOf(
      '\u2926' to '\u2925',
      '\u21B2' to '\u21B3',
      '\u2936' to '\u2937',
      '\u21A9' to '\u21AA',
      '\uE48B' to '\uE48C'
    ).map { it.first to it.second }

    private val KEY = Key<InlineCompletionSoftWrapManager>("inline.completion.soft.wrap.manager")

    override val key: Key<InlineCompletionSoftWrapManager>
      get() = KEY

    override fun create(editor: Editor) = InlineCompletionSoftWrapManager(editor)
  }
}