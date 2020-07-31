// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection.enhancedSwitch

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.enhancedSwitch.RedundantLabeledSwitchRuleCodeBlockInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class RedundantLabeledSwitchRuleCodeBlockTest  : LightJavaCodeInsightFixtureTestCase() {
  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(RedundantLabeledSwitchRuleCodeBlockInspection())
  }
  
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantLabeledSwitchRuleCodeBlock"

  fun testInExpression() = doTest()
  fun testInStatement() = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}