// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.psi.formatter.java

import com.intellij.JavaTestUtil
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.testFramework.LightPlatformCodeInsightTestCase

class AdjustWhitespaceLineTextBlockReformatPostProcessorTest : LightPlatformCodeInsightTestCase() {
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

  private fun getCommonSettings(): CommonCodeStyleSettings = currentCodeStyleSettings.getCommonSettings(JavaLanguage.INSTANCE)


  private fun getJavaSettings(): JavaCodeStyleSettings {
    return currentCodeStyleSettings.getCustomSettings(JavaCodeStyleSettings::class.java)
  }

  private fun doTest() {
    val testName = getTestName(false)
    configureByFile("$testName.java")
    WriteCommandAction.runWriteCommandAction(project) { CodeStyleManager.getInstance(project).reformatText(file, 0, editor.document.textLength) }
    checkResultByFile("${testName}_after.java")
  }


}