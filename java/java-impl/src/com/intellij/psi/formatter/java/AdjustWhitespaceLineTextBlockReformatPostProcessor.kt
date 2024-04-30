// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.util.PsiLiteralUtil
import com.intellij.psi.util.startOffset
import com.intellij.util.text.CharArrayUtil

class AdjustWhitespaceLineTextBlockReformatPostProcessor : PostFormatProcessor {
  override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
    processFile(source.containingFile, source.textRange, settings)
    return source
  }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    return processFile(source, rangeToReformat, settings)
  }

  private fun processFile(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {
    val documentManager = PsiDocumentManager.getInstance(source.project)
    val document = documentManager.getDocument(source) ?: return rangeToReformat

    val textBlockVisitor = TextBlockVisitor(rangeToReformat)
    source.accept(textBlockVisitor)

    var deltaLength = 0
    textBlockVisitor.whiteSpaceRangesWithIndentList.sortedByDescending {
      it.whiteSpaceRange.startOffset
    }.forEach { (range, indent) ->
      val newSpaces = buildWhitespaceString(settings, source.fileType, indent)
      deltaLength += newSpaces.length
      deltaLength -= range.length
      document.replaceString(range.startOffset, range.endOffset, newSpaces)
    }

    documentManager.commitDocument(document)
    return rangeToReformat.grown(deltaLength)
  }

  override fun isWhitespaceOnly(): Boolean = true

  private fun buildWhitespaceString(settings: CodeStyleSettings, fileType: FileType, indent: Int): String {
    val whitespaceSymbol = if (settings.useTabCharacter(fileType)) "\t" else " "
    return whitespaceSymbol.repeat(indent)
  }

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