// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
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

  private fun doTest() {
    myFixture.configureByFile("${getTestName(false)}.java")
    myFixture.checkHighlighting()
  }
}