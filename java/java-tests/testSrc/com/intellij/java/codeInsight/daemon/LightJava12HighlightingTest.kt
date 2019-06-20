// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.openapi.roots.LanguageLevelModuleExtension
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class LightJava12HighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_12
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlighting12"

  fun testEnhancedSwitchStatements() = doTest()
  fun testSwitchExpressions() = doTest()
  fun testSwitchExpressionsEnumResolve() = doTest()
  fun testValueBreaks() = doTest()
  fun testSwitchNumericPromotion() = doTest()
  fun testSimpleInferenceCases() = doTest()
  fun testEnhancedSwitchDefinitelyAssigned() = doTest()
  fun testEnhancedSwitchUnreachable() = doTest()
  fun testSwitchExpressionHasResult() = doTest()

  fun testYieldStatements() = try {
    level(LanguageLevel.JDK_13_PREVIEW)
    doTest()
  }
  finally {
    level(LanguageLevel.JDK_12_PREVIEW)
  }

  private fun level(level: LanguageLevel) =
    ModuleRootModificationUtil.updateModel(module) { it.getModuleExtension(LanguageLevelModuleExtension::class.java).languageLevel = level }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}