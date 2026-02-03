// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.hints.InlayPresentationFactory.Padding
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.VisualPosition
import com.intellij.util.DocumentUtil
import com.intellij.util.text.CharArrayUtil
import org.jetbrains.annotations.ApiStatus

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