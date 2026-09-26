// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.JavaTestUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class TextBlockBlankLinesFormatterTest : LightPlatformCodeInsightTestCase() {
  override fun getTestDataPath(): String = "${JavaTestUtil.getJavaTestDataPath()}/psi/formatter/java/textBlock/"

  fun testWhitespacesLessThanAlignment() = doTest()

  fun testWhitespacesMoreThanAlignment() = doTest()

  fun testMultipleLines() = doTest()

  fun testMultipleTextBlocks() = doTest()

  fun testTabCharacterWhitespacesLessThanAlignment() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    doTest()
  }

  fun testTabCharacterWhitespacesMoreThanAlignment() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    doTest()
  }

  fun testNonIntegerNumberOfTabs() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testAlignTextBlockWhitespacesLessThanAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testAlignTextBlockWhitespacesMoreThanAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testCaretInWhitespaceLineBeforeAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testCaretInWhitespaceLineAfterAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testCaretInWhitespaceLineInBeginning() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testCaretInWhitespaceLineInMiddleAfterAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testCaretInWhitespaceLineInMiddleBeforeAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  fun testLastLineWithoutSymbolsAfterAlignment() {
    doTest()
  }

  fun testLastLineWithSymbolsAfterAlignment() {
    doTest()
  }

  fun testLastLineWithoutSymbolsBeforeAlignment() {
    doTest()
  }

  fun testLastLineWithSymbolsBeforeAlignment() {
    doTest()
  }

  fun testEmptyLastLine() {
    doTest()
  }

  fun testEmptyLinesAlreadyEmpty() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesWhitespacesLessThanAlignment() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesWhitespacesMoreThanAlignment() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAtBeginning() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAtEnd() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesMultipleConsecutive() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesOnlyWhitespace() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAlternatingWithText() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesEscapedSpace() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesTabCharacter() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAlignTextBlock() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAlignedInitiallyEnd() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesAlignedInitiallyStart() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  fun testEmptyLinesReformatOnLiteral() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true

    doTestOnPsiElement(PsiLiteralExpression::class.java)
  }

  fun testEmptyLinesReformatOnElementContainingLiteral() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true

    doTestOnPsiElement(PsiMethod::class.java)
  }

  private fun getCommonSettings(): CommonCodeStyleSettings = currentCodeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)


  private fun getJavaSettings(): JavaCodeStyleSettings {
    return currentCodeStyleSettings.getCustomSettings(JavaCodeStyleSettings::class.java)
  }

  private fun <T: PsiElement> doTestOnPsiElement(clazz: Class<T>) {
    val testName = getTestName(false)
    configureByFile("$testName.java")

    val candidate = file.findElementAt(editor.caretModel.offset)
    check(candidate != null)
    val parent = PsiTreeUtil.getParentOfType(candidate, clazz)
    checkNotNull(parent)

    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformat(parent) }
    checkResultByFile("${testName}_after.java")
  }

  private fun doTest() {
    val testName = getTestName(false)
    configureByFile("$testName.java")
    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(file, 0, editor.document.textLength) }
    checkResultByFile("${testName}_after.java")
  }
}