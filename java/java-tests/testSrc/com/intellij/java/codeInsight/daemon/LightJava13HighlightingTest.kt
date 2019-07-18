// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class LightJava13HighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_13
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting13"

  fun testTextBlocks() = doTest()
  fun testUnclosedTextBlock() = doTest()

  fun testTextBlockOpeningSpaces() {
    myFixture.configureByText(getTestName(false) + ".java", "class C {\n  String spaces = \"\"\" \t \u000C \n    \"\"\";\n}")
    myFixture.checkHighlighting()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}