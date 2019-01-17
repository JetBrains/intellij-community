// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateBranchesInEnhancedSwitchTest : LightCodeInsightFixtureTestCase() {
  val inspection = DuplicateBranchesInSwitchInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateBranchesInEnhancedSwitch"

  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_12

  fun testSimpleExpression() = doTest()
  fun testSimpleStatement() = doTest()
  fun testThrowInExpression() = doTest()
  fun testThrowInStatement() = doTest()
  fun testReturnInStatement() = doTest()
  fun testExpressionParentheses() = doTest()
  fun testStatementParentheses() = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}