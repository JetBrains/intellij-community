// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.text.CharArrayUtil

class AdjustWhitespaceLineTextBlockProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    val textBlockVisitor = TextBlockVisitor(rangeToReformat)
    source.accept(textBlockVisitor)

    val document = source.fileDocument

    var deltaLength = 0
    textBlockVisitor.whiteSpaceRangesWithIndentList.sortedByDescending {
      it.whiteSpaceRange.startOffset
    }.forEach { (range, indent) ->
      val newSpaces = " ".repeat(indent)
      deltaLength += newSpaces.length
      deltaLength -= (range.endOffset - range.startOffset)
      document.replaceString(range.startOffset, range.endOffset, newSpaces)
    }

    return rangeToReformat.grown(deltaLength)
  }

  override fun isWhitespaceOnly(): Boolean = true

  private class TextBlockVisitor(private val rangeToReformat: TextRange) : JavaRecursiveElementVisitor() {
    val whiteSpaceRangesWithIndentList: List<WhiteSpaceRangeWithIndent>
      get() = _whiteSpaceRangesWithIndentList

    private val _whiteSpaceRangesWithIndentList: MutableList<WhiteSpaceRangeWithIndent> = mutableListOf()

    override fun visitLiteralExpression(literal: PsiLiteralExpression) {
      if (!literal.isTextBlock) {
        return
      }

      val indent = PsiLiteralUtil.getTextBlockIndent(literal)
      if (indent == -1) {
        return
      }
      val text = literal.text

      JavaFormatterUtil.extractTextRangesFromLiteralText(text, indent, true)
        .filter { textRange ->
          rangeToReformat.contains(textRange.shiftRight(literal.startOffset)) &&
          CharArrayUtil.isEmptyOrSpaces(literal.text, textRange.startOffset, textRange.endOffset)
        }
        .map { it.shiftRight(literal.startOffset) }
        .reversed()
        .forEach {
          _whiteSpaceRangesWithIndentList.add(WhiteSpaceRangeWithIndent(it, indent))
        }

    }
  }

  private data class WhiteSpaceRangeWithIndent(val whiteSpaceRange: TextRange, val indent: Int)
}