// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.text.CharArrayUtil

class AdjustWhitespaceLineTextBlockReformatPostProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    processFile(source.containingFile, source.textRange)
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    return processFile(source, rangeToReformat)
  }

  private fun processFile(source: PsiFile, rangeToReformat: TextRange): TextRange {
    val documentManager = PsiDocumentManager.getInstance(source.project)
    val document = documentManager.getDocument(source) ?: return rangeToReformat

    val textBlockVisitor = TextBlockVisitor(rangeToReformat)
    source.accept(textBlockVisitor)

    var deltaLength = 0
    textBlockVisitor.whiteSpaceRangesWithIndentList.sortedByDescending {
      it.whiteSpaceRange.startOffset
    }.forEach { (range, indentString) ->
      deltaLength += indentString.length
      deltaLength -= range.length
      document.deleteString(range.startOffset, range.endOffset)
      document.insertString(range.startOffset, indentString)
    }

    documentManager.commitDocument(document)
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

      val indentString = PsiLiteralUtil.getTextBlockIndentString(literal)
      if (indentString == null) {
        return
      }
      val text = literal.text

      JavaFormatterUtil.extractTextRangesFromLiteralText(text, indentString.length, true)
        .filter { textRange ->
          rangeToReformat.contains(textRange.shiftRight(literal.startOffset)) &&
          CharArrayUtil.isEmptyOrSpaces(literal.text, textRange.startOffset, textRange.endOffset)
        }
        .map { it.shiftRight(literal.startOffset) }
        .reversed()
        .forEach {
          _whiteSpaceRangesWithIndentList.add(WhiteSpaceRangeWithIndent(it, indentString))
        }

    }
  }

  private data class WhiteSpaceRangeWithIndent(val whiteSpaceRange: TextRange, val indentString: String)
}