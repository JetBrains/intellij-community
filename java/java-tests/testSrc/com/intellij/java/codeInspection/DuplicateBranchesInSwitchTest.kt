// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase

/**
 * @author Pavel.Dolgov
 */
class DuplicateBranchesInSwitchTest : LightCodeInsightFixtureTestCase() {
  val inspection = DuplicateBranchesInSwitchInspection()

  override fun setUp() {
    super.setUp()
    myFixture.enableInspections(inspection)
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/duplicateBranchesInSwitch"

  fun testSimple() = doTest()
  fun testReturn() = doTest()
  fun testThrow() = doTest()
  fun testContinue() = doTest()
  fun testFallThrough() = doTest()
  fun testFallThroughBefore() = doTest()
  fun testAllFallThrough() = doTest()
  fun testNoLastBreak() = doTest()
  fun testFallThroughToBreak() = doTest()
  fun testThreeDuplicates() = doTest()
  fun testThreeDuplicatesDefault() = doTest()
  fun testDuplicateAfterDefault() = doTest()
  fun testTwoCaseLabels() = doTest()
  fun testComplexBranches() = doTest()
  fun testBreakWithLabel() = doTest()
  fun testBreakAndReturnUnderIf() = doTest()
  fun testReturnWithComments() = doTest()
  fun testUnaryMinusInReturn() = doTest()
  fun testMethodCallInReturn() = doTest()
  fun testManySimilarBranches() = doTest()
  fun testParentheses() = doTest()
  fun testAssignment() = doTest()

  private fun doTest() {
    myFixture.testHighlighting("${getTestName(false)}.java")
  }
}