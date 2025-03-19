// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class DuplicateBranchesInSwitchTest : LightJavaCodeInsightFixtureTestCase() {
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
  fun testDuplicateFallThrough() = doTest()
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
  fun testMethodReferences() = doTest()
  fun testNoExceptionWhenFirstLabelIsMissing() = doTest()
  fun testUnresolvedQualifier() = doTest()
  fun testCatchTypeReference() = doTest()
  fun testNullDuplicatesPattern() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testPatternDuplicatesNull() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }

  private fun doTest() {
    myFixture.testHighlighting(true, true, true,"${getTestName(false)}.java")
  }
}