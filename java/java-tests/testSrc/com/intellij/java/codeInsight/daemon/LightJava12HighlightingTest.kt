// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

class LightJava12HighlightingTest : LightCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_12
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting12"

  fun testEnhancedSwitchStatements() = doTest()
  fun testSwitchExpressions() = doTest()
  fun testValueBreaks() = doTest()
  fun testSwitchNumericPromotion() = doTest()
  fun testSimpleInferenceCases() = doTest()
  fun testEnhancedSwitchDefinitelyAssigned() = doTest()
  fun testEnhancedSwitchUnreachable() = doTest()
  fun testSwitchExpressionHasResult() = doTest()

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}