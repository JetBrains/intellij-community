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
    val visibleAreaWidth = softWrapModel.applianceManager.widthProvider.visibleAreaWidth
    if (!isEditorWideEnough(visibleAreaWidth, volumetricFactory)) {
      // Can't do anything
      return null
    }

    val linesToFix = LinkedList(lines)
    val resultLines = mutableListOf<List<VolumetricInlineCompletionTextBlock>>()
    while (linesToFix.isNotEmpty()) {
      val line = linesToFix.first()
      val blocks = line.blocks
      if (blocks.isEmpty()) {
        resultLines.add(blocks)
        linesToFix.removeFirst()
        continue
      }

      val startX = line.startX
      val editorAvailableLineWidth = visibleAreaWidth - startX
      val (leftSplitPart, rightSplitPart) = splitByAvailableWidth(blocks, editorAvailableLineWidth, volumetricFactory)

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
          resultLines.add(blocks)
          linesToFix.removeFirst()
        }
        continue
      }
      if (leftSplitPart.areArrowsOnly()) { // We can't split the line. Just arrows will be added in an infinite loop if we keep going.
        resultLines.add(blocks)
        linesToFix.removeFirst()
        continue
      }
      resultLines.add(leftSplitPart)
      linesToFix.removeFirst()
      linesToFix.addFirst(RenderedLine(rightSplitPart, 0)) // 0 because it's a block inlay, for sure
    }

    return resultLines
  }

  /**
   * If [editorWidth] is too small (less than 3 characters), there is no point to try to do anything.
   */
  private fun isEditorWideEnough(editorWidth: Int, volumetricFactory: InlineCompletionVolumetricTextBlockFactory): Boolean {
    val block = InlineCompletionRenderTextBlock("abc", TextAttributes())
    val volumetricBlock = volumetricFactory.getVolumetric(block)
    return volumetricBlock.widthInPixels <= editorWidth
  }

  /**
   * Splits [blocks] into two parts based on the available width for the first part.
   *
   * Split may be done:
   * * By whitespace character. In this case, the whitespace block is visually removed and replaced with the only one split-space.
   * * If there is no whitespace character we can split by, we split it by the first non-fitting character.
   */
  private fun splitByAvailableWidth(
    blocks: List<VolumetricInlineCompletionTextBlock>,
    availableWidth: Int,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory,
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>> {
    if (blocks.isEmpty()) {
      return blocks to emptyList()
    }
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

    // This is for the case if we cannot find a whitespace to split. Then, we split by the first non-fitting character.
    var potentialResultIfNoWhitespace: Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>>? = null

    while (currentIndex >= 0) {
      currentWidth -= blocks[currentIndex].widthInPixels

      val blocksPrefix = blocks.subList(0, currentIndex)
      val blocksSuffix = blocks.subList(currentIndex + 1, blocks.size)
      val currentBlock = blocks[currentIndex]

      trySplitByWhitespace(
        blocksPrefix = blocksPrefix,
        blocksSuffix = blocksSuffix,
        currentBlock = currentBlock,
        prefixWidth = currentWidth,
        availableWidth = availableWidth,
        volumetricFactory = volumetricFactory,
      )?.let { return it }

      if (potentialResultIfNoWhitespace == null) {
        potentialResultIfNoWhitespace = trySplitByAnyCharacter(
          blocksPrefix = blocksPrefix,
          blocksSuffix = blocksSuffix,
          currentBlock = currentBlock,
          prefixWidth = currentWidth,
          availableWidth = availableWidth,
          volumetricFactory = volumetricFactory,
        )
      }

      currentIndex--
    }

    if (potentialResultIfNoWhitespace != null) {
      return potentialResultIfNoWhitespace
    }

    return emptyList<VolumetricInlineCompletionTextBlock>() to blocks
  }

  /**
   * Tries to find a whitespace in [currentBlock] that can be used as a split point.
   *
   * Criteria of the successful split. Let's say that `currentBlock = left + right`.
   * Then the successful split is:
   * `width(blocksPrefix + left) <= availableWidth`.
   *
   * We add special arrows to `left` and `right` after splitting to make the result more readable.
   *
   * Note: we remove whitespaces around the split point because they only disrupt reading.
   *
   * @param prefixWidth the width of [blocksPrefix]
   *
   * @return `null` if there is no whitespace that can be split.
   * Otherwise, returns a pair of the split: ([blocksPrefix] + `left`, `right` + [blocksSuffix]).
   */
  private fun trySplitByWhitespace(
    blocksPrefix: List<VolumetricInlineCompletionTextBlock>,
    blocksSuffix: List<VolumetricInlineCompletionTextBlock>,
    currentBlock: VolumetricInlineCompletionTextBlock,
    prefixWidth: Double,
    availableWidth: Int,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory,
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>>? {
    val text = currentBlock.block.text
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

      val left = InlineCompletionRenderTextBlock(text.substring(0, splitStart + 1), currentBlock.block.attributes)
      val right = InlineCompletionRenderTextBlock(text.substring(currentCharIndex + 1), currentBlock.block.attributes)
      trySplit(
        blocksPrefix = blocksPrefix,
        blocksSuffix = blocksSuffix,
        toLeft = left,
        toRight = right,
        prefixWidth = prefixWidth,
        availableWidth = availableWidth,
        volumetricFactory = volumetricFactory
      )?.let { return it }

      currentCharIndex = splitStart
    }

    return null
  }

  /**
   * Tries to find a non-whitespace character in [currentBlock] that can be used as a split point.
   *
   * Similar to [trySplitByWhitespace], but we split by any character. It is used only in the case we cannot find appropriate whitespace.
   */
  private fun trySplitByAnyCharacter(
    blocksPrefix: List<VolumetricInlineCompletionTextBlock>,
    blocksSuffix: List<VolumetricInlineCompletionTextBlock>,
    currentBlock: VolumetricInlineCompletionTextBlock,
    prefixWidth: Double,
    availableWidth: Int,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory,
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>>? {
    val text = currentBlock.block.text

    // We don't need to check anything after the first whitespace. It's already been checked.
    val searchLimit = text.indexOfFirst { it.isWhitespace() }.takeIf { it >= 0 } ?: text.length

    // Try to split into [0, charIndex) and [charIndex, text.length)
    // We cannot use `charIndex = 0`, otherwise we might end up in an endless loop where we add empty lines just with arrows
    for (currentCharIndex in searchLimit downTo 1) {
      val left = InlineCompletionRenderTextBlock(text.substring(0, currentCharIndex), currentBlock.block.attributes)
      val right = InlineCompletionRenderTextBlock(text.substring(startIndex = currentCharIndex), currentBlock.block.attributes)
      trySplit(
        blocksPrefix = blocksPrefix,
        blocksSuffix = blocksSuffix,
        toLeft = left,
        toRight = right,
        prefixWidth = prefixWidth,
        availableWidth = availableWidth,
        volumetricFactory = volumetricFactory
      )?.let { return it }
    }

    return null
  }

  /**
   * Verifies that it's possible to have the next split: ([blocksPrefix] + [toLeft] + `arrow`, `arrow` + [toRight] + [blocksSuffix]).
   *
   * @param prefixWidth the width of [blocksPrefix]
   *
   * @return `null` if it's impossible to have the next split.
   * Otherwise, returns a pair of the split: ([blocksPrefix] + [toLeft] + `arrow`, `arrow`, [toRight] + [blocksSuffix]).
   *
   * @see [trySplitByWhitespace]
   * @see [trySplitByAnyCharacter]
   */
  private fun trySplit(
    blocksPrefix: List<VolumetricInlineCompletionTextBlock>,
    blocksSuffix: List<VolumetricInlineCompletionTextBlock>,
    toLeft: InlineCompletionRenderTextBlock,
    toRight: InlineCompletionRenderTextBlock,
    prefixWidth: Double,
    availableWidth: Int,
    volumetricFactory: InlineCompletionVolumetricTextBlockFactory,
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>>? {
    val leftBlocks = listOfNotNull(
      if (toLeft.text.isNotEmpty()) InlineCompletionRenderTextBlock(toLeft.text, toLeft.attributes) else null,
      // TODO the arrow doesn't inherit background of the current caret row
      getSoftWrapArrow(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
    ).toVolumetric(volumetricFactory)

    val leftWidth = accumulatedWidthToInt(leftBlocks.sumOf { it.widthInPixels })
    if (accumulatedWidthToInt(prefixWidth + leftWidth) <= availableWidth) {
      val rightBlocks = listOfNotNull(
        getSoftWrapArrow(SoftWrapDrawingType.AFTER_SOFT_WRAP),
        if (toRight.text.isNotEmpty()) InlineCompletionRenderTextBlock(toRight.text, toRight.attributes) else null,
      ).toVolumetric(volumetricFactory)

      val finalLeft = blocksPrefix + leftBlocks
      val finalRight = rightBlocks + blocksSuffix
      return finalLeft to finalRight
    }

    return null
  }

  private fun getSoftWrapArrow(type: SoftWrapDrawingType): InlineCompletionRenderTextBlock? {
    val arrows = this.arrows ?: return null
    val text = when (type) {
      SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED -> arrows.first
      SoftWrapDrawingType.AFTER_SOFT_WRAP -> arrows.second
    }
    return InlineCompletionRenderTextBlock(text, getArrowAttributes())
  }

  private fun List<VolumetricInlineCompletionTextBlock>.areArrowsOnly(): Boolean {
    return all { it.block.text == arrows?.first || it.block.text == arrows?.second }
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
    )

    private val KEY = Key<InlineCompletionSoftWrapManager>("inline.completion.soft.wrap.manager")

    override val key: Key<InlineCompletionSoftWrapManager>
      get() = KEY

    override fun create(editor: Editor) = InlineCompletionSoftWrapManager(editor)
  }
}