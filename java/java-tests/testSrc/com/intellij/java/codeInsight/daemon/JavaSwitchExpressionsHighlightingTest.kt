// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix
import com.intellij.codeInspection.redundantCast.RedundantCastInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_15
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class JavaSwitchExpressionsHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_15)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = "${JavaTestUtil.getJavaTestDataPath()}/codeInsight/daemonCodeAnalyzer/switchExpressions"
  }

  @Test
  fun testEnhancedSwitchStatements(): Unit = doTest()
  @Test
  fun testSwitchExpressions(): Unit = doTest()
  @Test
  fun testSwitchExpressionsNoResult(): Unit = doTest()
  @Test
  fun testSwitchExpressionsEnumResolve(): Unit = doTest()
  @Test
  fun testSwitchNumericPromotion(): Unit = doTest()
  @Test
  fun testSimpleInferenceCases(): Unit = doTest()
  @Test
  fun testEnhancedSwitchDefinitelyAssigned(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testEnhancedSwitchUnreachable(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testSwitchExpressionHasResult(): Unit = doTest()
  @Test
  fun testYieldStatements(): Unit = doTest()
  @Test
  fun testAssignToFinalInSwitchExpression(): Unit = doTest()
  @Test
  fun testDeadCode(): Unit = doTest()
  @Test
  fun testComplexTernaryInSwitch(): Unit = doTest()
  @Test
  fun testQualifiedEnumInSwitch(): Unit =  IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testConstantAssignment(): Unit =  IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testEnumDuplicates(): Unit =  IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testDuplicatedWithCast(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testRedundantCastInSwitchBranch() {
    myFixture.enableInspections(RedundantCastInspection())
    doTest()
  }
  @Test
  fun testIncompleteSwitch(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testIncompleteSwitchFixColon(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { it is InsertMissingTokenFix }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${myFixture.getTestName(false)}_after.java")
  }

  @Test
  fun testIncompleteSwitchFixArray(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { it is InsertMissingTokenFix }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${myFixture.getTestName(false)}_after.java")
  }
  @Test
  fun testDeeplyNested(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_21) { doTest() }
  @Test
  fun testYieldUnnamedLambdaInIfElse(): Unit = IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_22) { doTest() }

  private fun doTest() {
    myFixture.configureByFile("${myFixture.getTestName(false)}.java")
    myFixture.checkHighlighting()
  }
}