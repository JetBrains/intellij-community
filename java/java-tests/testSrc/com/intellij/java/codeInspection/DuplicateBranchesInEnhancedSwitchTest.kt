// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

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
  fun testEmptyBodiesCanBeMerge() = doTest()
  fun testEmptyBodiesCannotBeMerge() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testGuardedPatternMergeWithNull() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testNullMergeWithGuardedPattern() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testRecordPattern1() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testRecordPattern2() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testRecordPattern3() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testTwoPatterns() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testWhenClause1() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testWhenClause2() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testExpressionsWithComments() = doTest()
  fun testNullDuplicatesPattern() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_20_PREVIEW) { doTest() }
  fun testPatternDuplicatesNull() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_20_PREVIEW) { doTest() }
  fun testNullDuplicatesDefault() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_20_PREVIEW) { doTest() }
  fun testMixedCases() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21_PREVIEW) { doTest() }
  fun testDominatedUnnamedVariables() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21_PREVIEW) { doTest() }
  private fun doTest() {
    myFixture.enableInspections(DuplicateBranchesInSwitchInspection())
    myFixture.testHighlighting("${getTestName(false)}.java")
  }

  override fun getProjectDescriptor(): LightProjectDescriptor {
    return JAVA_19
  }
}