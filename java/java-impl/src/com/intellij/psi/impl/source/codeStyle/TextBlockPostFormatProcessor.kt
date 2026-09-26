// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.codeStyle

import com.intellij.openapi.util.TextRange
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.JavaRecursiveElementVisitor
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.formatter.java.JavaFormatterUtil
import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.psi.util.startOffset

internal class TextBlockPostFormatProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    if (!isApplicable(settings)) return source

    if (source is PsiLiteralExpression) {
      if (!source.isTextBlock) return source
      return processLiteral(source) ?: source
    }

    val visitor = TextBlockCollector()
    source.accept(visitor)

    visitor.textBlockList.forEach { processLiteral(it) }

    return source
  }

  private fun processLiteral(literal: PsiLiteralExpression): PsiElement? {
    val indent = PsiLiteralUtil.getTextBlockIndent(literal)
    if (indent == -1) return null

    val visitor = WhitespaceRangeCollector(literal.textRange, false)
    literal.accept(visitor)

    val sb = StringBuilder(literal.text)
    visitor.whiteSpaceRangesList.forEach { range -> sb.delete(range.startOffset, range.endOffset) }

    val factory = JavaPsiFacade.getElementFactory(literal.project)
    val newExpression = factory.createExpressionFromText(sb.toString(), literal)
    return literal.replace(newExpression)
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    if (!isApplicable(settings)) return rangeToReformat
    return processFile(source, rangeToReformat)
  }

  private fun processFile(source: PsiFile, rangeToReformat: TextRange): TextRange {
    val documentManager = PsiDocumentManager.getInstance(source.project)
    val document = documentManager.getDocument(source) ?: return rangeToReformat

    val collector = WhitespaceRangeCollector(rangeToReformat, true)
    source.accept(collector)

    var lengthDelta = 0
    collector.whiteSpaceRangesList.forEach { range ->
      lengthDelta -= range.length
      document.deleteString(range.startOffset, range.endOffset)
    }

    documentManager.commitDocument(document)
    return rangeToReformat.grown(lengthDelta)
  }

  private fun isApplicable(settings: CodeStyleSettings): Boolean {
    val javaSettings = settings.getCustomSettings(JavaCodeStyleSettings::class.java)
    return javaSettings.STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS
  }

  override fun isWhitespaceOnly(): Boolean = true

  private class TextBlockCollector : JavaRecursiveElementVisitor() {
    val textBlockList: List<PsiLiteralExpression>
      get() = _textBlockList

    private val _textBlockList: MutableList<PsiLiteralExpression> = mutableListOf()

    override fun visitLiteralExpression(expression: PsiLiteralExpression) {
      if (expression.isTextBlock) {
        _textBlockList.add(expression)
      }
    }
  }

  private class WhitespaceRangeCollector(private val rangeToReformat: TextRange, private val shiftInFile: Boolean) : JavaRecursiveElementVisitor() {
    val whiteSpaceRangesList: List<TextRange>
      get() = _whiteSpaceRangesList.sortedByDescending { range -> range.startOffset }

    private val _whiteSpaceRangesList: MutableList<TextRange> = mutableListOf()

    override fun visitLiteralExpression(literal: PsiLiteralExpression) {
      if (!literal.isTextBlock) {
        return
      }

      val indent = PsiLiteralUtil.getTextBlockIndent(literal)
      if (indent == -1) {
        return
      }
      val text = literal.text

      JavaFormatterUtil.extractTextRangesFromLiteralText(text, indent)
        .filter { textRange ->
          textRange.isEmpty && rangeToReformat.intersects(textRange.shiftRight(literal.startOffset))
        }
        .map { range ->
          val adjustedRange = TextRange(range.startOffset - indent, range.endOffset)
          if (shiftInFile) adjustedRange.shiftRight(literal.startOffset) else adjustedRange
        }
        .forEach {
          _whiteSpaceRangesList.add(it)
        }
    }
  }
}