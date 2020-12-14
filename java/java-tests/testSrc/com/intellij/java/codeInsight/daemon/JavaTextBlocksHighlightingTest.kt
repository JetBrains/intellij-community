// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JavaTextBlocksHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_15
  override fun getBasePath() = "${JavaTestUtil.getRelativeJavaTestDataPath()}/codeInsight/daemonCodeAnalyzer/textBlocks"

  fun testTextBlocks() = doTest()
  fun testUnclosedTextBlock() = doTest()
  fun testUnclosedTextBlock2() = doTest()

  fun testTextBlockOpeningSpaces() {
    myFixture.configureByText("${getTestName(false)}.java", "class C {\n  String spaces = \"\"\" \t \u000C \n    \"\"\";\n}")
    myFixture.checkHighlighting()
  }

  private fun doTest() {
    myFixture.configureByFile("${getTestName(false)}.java")
    myFixture.checkHighlighting()
  }

  fun testEscapeQuotes() = doTestPaste("\"\"\"\ntarget\"\"\"")

  fun testEscapeQuotes2() = doTestPaste("\"\ntarget\"")

  fun testNoEscapeWhenNotInTextBlockContent() = doTestPaste("\\")

  fun testNoEscapeWhenInTextBlockPrefix() = doTestPaste("\\")

  fun testPasteNewline() = doTestPaste("\n\n")

  fun testPasteMultilineText() = doTestPaste("<html>  \n  <body>\n  </body>\n</html>  ")

  fun testBadEscape() = doTestPaste("\\");

  private fun doTestPaste(textToPaste: String) {
    myFixture.configureByText("plain.txt", "<selection>$textToPaste</selection>")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_COPY)
    myFixture.configureByFile("${getTestName(false)}.java")
    myFixture.performEditorAction(IdeActions.ACTION_EDITOR_PASTE)
    myFixture.checkResultByFile("${getTestName(false)}.after.java")
  }

}