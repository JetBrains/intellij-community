// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix
import com.intellij.codeInspection.redundantCast.RedundantCastInspection
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class JavaSwitchExpressionsHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_15
  override fun getBasePath() = "${JavaTestUtil.getRelativeJavaTestDataPath()}/codeInsight/daemonCodeAnalyzer/switchExpressions"

  fun testEnhancedSwitchStatements() = doTest()
  fun testSwitchExpressions() = doTest()
  fun testSwitchExpressionsNoResult() = doTest()
  fun testSwitchExpressionsEnumResolve() = doTest()
  fun testSwitchNumericPromotion() = doTest()
  fun testSimpleInferenceCases() = doTest()
  fun testEnhancedSwitchDefinitelyAssigned() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testEnhancedSwitchUnreachable() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testSwitchExpressionHasResult() = doTest()
  fun testYieldStatements() = doTest()
  fun testAssignToFinalInSwitchExpression() = doTest()
  fun testDeadCode() = doTest()
  fun testComplexTernaryInSwitch() = doTest()
  fun testQualifiedEnumInSwitch() =  IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testConstantAssignment() =  IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testEnumDuplicates() =  IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testDuplicatedWithCast() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testRedundantCastInSwitchBranch() {
    myFixture.enableInspections(RedundantCastInspection())
    doTest()
  }
  fun testIncompleteSwitch() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) { doTest() }
  fun testIncompleteSwitchFixColon() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { it is InsertMissingTokenFix }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${getTestName(false)}_after.java")
  }

  fun testIncompleteSwitchFixArray() = IdeaTestUtil.withLevel(module, LanguageLevel.JDK_21) {
    doTest()
    val availableIntentions = myFixture.availableIntentions
      .mapNotNull { it.asModCommandAction()  }
      .filter { it is InsertMissingTokenFix }
    assertEquals(1, availableIntentions.size)
    myFixture.launchAction(availableIntentions.first().asIntention())
    myFixture.checkResultByFile("${getTestName(false)}_after.java")
  }

  private fun doTest() {
    myFixture.configureByFile("${getTestName(false)}.java")
    myFixture.checkHighlighting()
  }
}