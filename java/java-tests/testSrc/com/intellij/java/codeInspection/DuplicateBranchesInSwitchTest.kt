// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInspection.DuplicateBranchesInSwitchInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class DuplicateBranchesInSwitchTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture()
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateBranchesInSwitch"
  }

  val inspection = DuplicateBranchesInSwitchInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testSimple(): Unit = doTest()
  @Test
  fun testReturn(): Unit = doTest()
  @Test
  fun testThrow(): Unit = doTest()
  @Test
  fun testContinue(): Unit = doTest()
  @Test
  fun testFallThrough(): Unit = doTest()
  @Test
  fun testFallThroughBefore(): Unit = doTest()
  @Test
  fun testAllFallThrough(): Unit = doTest()
  @Test
  fun testDuplicateFallThrough(): Unit = doTest()
  @Test
  fun testNoLastBreak(): Unit = doTest()
  @Test
  fun testFallThroughToBreak(): Unit = doTest()
  @Test
  fun testThreeDuplicates(): Unit = doTest()
  @Test
  fun testThreeDuplicatesDefault(): Unit = doTest()
  @Test
  fun testDuplicateAfterDefault(): Unit = doTest()
  @Test
  fun testTwoCaseLabels(): Unit = doTest()
  @Test
  fun testComplexBranches(): Unit = doTest()
  @Test
  fun testBreakWithLabel(): Unit = doTest()
  @Test
  fun testBreakAndReturnUnderIf(): Unit = doTest()
  @Test
  fun testReturnWithComments(): Unit = doTest()
  @Test
  fun testUnaryMinusInReturn(): Unit = doTest()
  @Test
  fun testMethodCallInReturn(): Unit = doTest()
  @Test
  fun testManySimilarBranches(): Unit = doTest()
  @Test
  fun testParentheses(): Unit = doTest()
  @Test
  fun testAssignment(): Unit = doTest()
  @Test
  fun testMethodReferences(): Unit = doTest()
  @Test
  fun testNoExceptionWhenFirstLabelIsMissing(): Unit = doTest()
  @Test
  fun testUnresolvedQualifier(): Unit = doTest()
  @Test
  fun testCatchTypeReference(): Unit = doTest()
  @Test
  fun testNullDuplicatesPattern(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testPatternDuplicatesNull(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }

  private fun doTest() {
    myFixture.testHighlighting(true, true, true,"${myFixture.getTestName(false)}.java")
  }
}