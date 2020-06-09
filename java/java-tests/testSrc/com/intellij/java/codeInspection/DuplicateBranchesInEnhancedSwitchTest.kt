// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateBranchesInEnhancedSwitchTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateBranchesInEnhancedSwitch"

  fun testSimpleExpression() = doTest()
  fun testSimpleStatement() = doTest()
  fun testThrowInExpression() = doTest()
  fun testThrowInStatement() = doTest()
  fun testReturnInStatement() = doTest()
  fun testExpressionParentheses() = doTest()
  fun testStatementParentheses() = doTest()
  fun testCaseLabelsExpression() = doTest()
  fun testCaseLabelsExpressionDefaultFirst() = doTest()
  fun testCaseLabelsExpressionDefaultLast() = doTest()
  fun testCaseLabelsExpressionDifferentComments() = doTest()
  fun testCaseLabelsExpressionSameComments() = doTest()

  private fun doTest() {
    myFixture.enableInspections(DuplicateBranchesInSwitchInspection())
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}