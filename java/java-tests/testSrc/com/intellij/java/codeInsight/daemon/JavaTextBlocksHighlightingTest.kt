// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaTextBlocksHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/daemonCodeAnalyzer/textBlocks"
  }

  @Test
  fun testTextBlocks(): Unit = doTest()
  @Test
  fun testUnclosedTextBlock(): Unit = doTest()
  @Test
  fun testUnclosedTextBlock2(): Unit = doTest()

  @Test
  fun testTextBlockOpeningSpaces() {
    myFixture.configureByText("${myFixture.getTestName(false)}.java", "class C {\n  String spaces = \"\"\" \t \u000C \n    \"\"\";\n}")
    myFixture.checkHighlighting()
  }

  private fun doTest() {
    myFixture.configureByFile("${myFixture.getTestName(false)}.java")
    myFixture.checkHighlighting()
  }

  @Test
  fun testEscapeQuotes(): Unit = doTestPaste("\"\"\"\ntarget\"\"\"")

  @Test
  fun testEscapeQuotes2(): Unit = doTestPaste("\"\ntarget\"")

  @Test
  fun testNoEscapeWhenNotInTextBlockContent(): Unit = doTestPaste("\\")

  @Test
  fun testNoEscapeWhenInTextBlockPrefix(): Unit = doTestPaste("\\")

  @Test
  fun testPasteNewline(): Unit = doTestPaste("\n\n")

  @Test
  fun testPasteMultilineText(): Unit = doTestPaste("<html>  \n  <body>\n  </body>\n</html>  ")

  @Test
  fun testBadEscape(): Unit = doTestPaste("\\")

  private fun doTestPaste(textToPaste: String) {
    myFixture.configureByText("plain.txt", "<selection>$textToPaste</selection>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY)
    myFixture.configureByFile("${myFixture.getTestName(false)}.java")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    myFixture.checkResultByFile("${myFixture.getTestName(false)}.after.java")
  }

}