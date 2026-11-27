// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.JavaTestUtil
import com.intellij.application.options.CodeStyle
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class TextBlockBlankLinesFormatterTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUp() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/psi/formatter/java/textBlock/"
  }

  @Test
  fun testWhitespacesLessThanAlignment(): Unit = doTest()

  @Test
  fun testWhitespacesMoreThanAlignment(): Unit = doTest()

  @Test
  fun testMultipleLines(): Unit = doTest()

  @Test
  fun testMultipleTextBlocks(): Unit = doTest()

  @Test
  fun testTabCharacterWhitespacesLessThanAlignment() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    doTest()
  }

  @Test
  fun testTabCharacterWhitespacesMoreThanAlignment() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    doTest()
  }

  @Test
  fun testNonIntegerNumberOfTabs() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testAlignTextBlockWhitespacesLessThanAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testAlignTextBlockWhitespacesMoreThanAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testCaretInWhitespaceLineBeforeAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testCaretInWhitespaceLineAfterAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testCaretInWhitespaceLineInBeginning() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testCaretInWhitespaceLineInMiddleAfterAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testCaretInWhitespaceLineInMiddleBeforeAlignment() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testLastLineWithoutSymbolsAfterAlignment() {
    doTest()
  }

  @Test
  fun testLastLineWithSymbolsAfterAlignment() {
    doTest()
  }

  @Test
  fun testLastLineWithoutSymbolsBeforeAlignment() {
    doTest()
  }

  @Test
  fun testLastLineWithSymbolsBeforeAlignment() {
    doTest()
  }

  @Test
  fun testEmptyLastLine() {
    doTest()
  }

  @Test
  fun testEmptyLinesAlreadyEmpty() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesWhitespacesLessThanAlignment() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesWhitespacesMoreThanAlignment() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAtBeginning() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAtEnd() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesMultipleConsecutive() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesOnlyWhitespace() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAlternatingWithText() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesEscapedSpace() {
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesTabCharacter() {
    getCommonSettings().indentOptions?.USE_TAB_CHARACTER = true
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAlignTextBlock() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = true
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAlignedInitiallyEnd() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesAlignedInitiallyStart() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true
    doTest()
  }

  @Test
  fun testEmptyLinesReformatOnLiteral() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true

    doTestOnPsiElement(PsiLiteralExpression::class.java)
  }

  @Test
  fun testEmptyLinesReformatOnElementContainingLiteral() {
    getJavaSettings().ALIGN_MULTILINE_TEXT_BLOCKS = false
    getJavaSettings().STRIP_WHITESPACE_FROM_BLANK_LINES_IN_TEXT_BLOCKS = true

    doTestOnPsiElement(PsiMethod::class.java)
  }

  private fun getCommonSettings(): CommonCodeStyleSettings = CodeStyle.getSettings(myFixture.project).getCommonSettings(JavaLanguage.INSTANCE)


  private fun getJavaSettings(): JavaCodeStyleSettings {
    return CodeStyle.getSettings(myFixture.project).getCustomSettings(JavaCodeStyleSettings::class.java)
  }

  private fun <T: PsiElement> doTestOnPsiElement(clazz: Class<T>) {
    val testName = myFixture.getTestName(false)
    myFixture.configureByFile("$testName.java")

    val parent = runReadActionBlocking {
      val candidate = myFixture.file.findElementAt(myFixture.editor.caretModel.offset)
      check(candidate != null)
      PsiTreeUtil.getParentOfType(candidate, clazz)
    }
    checkNotNull(parent)

    WriteCommandAction.runWriteCommandAction(myFixture.project) { CodeStyleManager.getInstance(myFixture.project).reformat(parent) }
    myFixture.checkResultByFile("${testName}_after.java")
  }

  private fun doTest() {
    val testName = myFixture.getTestName(false)
    myFixture.configureByFile("$testName.java")
    WriteCommandAction.runWriteCommandAction(myFixture.project) {
      CodeStyleManager.getInstance(myFixture.project).reformatText(myFixture.file, 0, myFixture.editor.document.textLength)
    }
    myFixture.checkResultByFile("${testName}_after.java")
  }
}