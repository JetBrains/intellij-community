// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_19
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class DuplicateBranchesInEnhancedSwitchTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_19)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateBranchesInEnhancedSwitch"
  }

  @Test
  fun testSimpleExpression(): Unit = doTest()
  @Test
  fun testSimpleStatement(): Unit = doTest()
  @Test
  fun testThrowInExpression(): Unit = doTest()
  @Test
  fun testThrowInStatement(): Unit = doTest()
  @Test
  fun testReturnInStatement(): Unit = doTest()
  @Test
  fun testExpressionParentheses(): Unit = doTest()
  @Test
  fun testStatementParentheses(): Unit = doTest()
  @Test
  fun testCaseLabelsExpression(): Unit = doTest()
  @Test
  fun testCaseLabelsExpressionDefaultFirst(): Unit = doTest()
  @Test
  fun testCaseLabelsExpressionDefaultLast(): Unit = doTest()
  @Test
  fun testCaseLabelsExpressionDifferentComments(): Unit = doTest()
  @Test
  fun testCaseLabelsExpressionSameComments(): Unit = doTest()
  @Test
  fun testEmptyBodiesCanBeMerge(): Unit = doTest()
  @Test
  fun testEmptyBodiesCannotBeMerge(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testGuardedPatternMergeWithNull(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testNullMergeWithGuardedPattern(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testRecordPattern1(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testRecordPattern2(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testRecordPattern3(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testTwoPatterns(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testWhenClause1(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testWhenClause2(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testExpressionsWithComments(): Unit = doTest()
  @Test
  fun testNullDuplicatesPattern(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testPatternDuplicatesNull(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testNullDuplicatesDefault(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testMixedCases(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21_PREVIEW) { doTest() }
  @Test
  fun testDominatedUnnamedVariables(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21_PREVIEW) { doTest() }
  @Test
  fun testDefaultWithDominated(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_25) { doTest() }
  @Test
  fun testNextDominated(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_25) { doTest() }

  @Test
  fun testSkipUnnamedForPreviousVersions(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  private fun doTest() {
    myFixture.enableInspections(DuplicateBranchesInSwitchInspection())
    myFixture.testHighlighting("${myFixture.getTestName(false)}.java")
  }
}