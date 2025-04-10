// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.inline.completion.render

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

internal class InlineCompletionSoftWrapManager private constructor(private val editor: Editor) : Disposable {

  private val arrows by lazy { chooseArrows() }

  fun getSoftWrapModelIfEnabled(): SoftWrapModelImpl? {
    if (!isSoftWrappingEnabled()) {
      return null
    }
    return editor.softWrapModel as? SoftWrapModelImpl
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
  ): Pair<List<VolumetricInlineCompletionTextBlock>, List<VolumetricInlineCompletionTextBlock>> {
    var currentIndex = 0 // the first over-width block
    var currentWidth = 0
    for ((_, width) in blocks) {
      currentWidth += width
      if (currentWidth <= availableWidth) {
        currentIndex++
      }
      else {
        break
      }
    }
    if (currentWidth <= availableWidth) {
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
          getSoftWrapArrow(SoftWrapDrawingType.BEFORE_SOFT_WRAP_LINE_FEED)
        ).toVolumetric(editor, roundUp = true)

        val leftWidth = leftBlocks.sumOf { it.widthInPixels }
        if (currentWidth + leftWidth <= availableWidth) {
          val right = text.substring(currentCharIndex + 1)
          val rightBlocks = listOfNotNull(
            getSoftWrapArrow(SoftWrapDrawingType.AFTER_SOFT_WRAP),
            if (right.isNotEmpty()) InlineCompletionRenderTextBlock(right, block.attributes) else null,
          ).toVolumetric(editor, roundUp = true)
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