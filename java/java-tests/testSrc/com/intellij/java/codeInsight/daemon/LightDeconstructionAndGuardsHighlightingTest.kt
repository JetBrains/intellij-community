// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class LightDeconstructionAndGuardsHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_19
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/deconstructionAndGuards"

  fun testSwitchExpression() {
    doTest()
  }

  fun testSwitchStatement() {
    doTest()
  }

  fun testIfStatement() {
    doTest()
  }

  fun testImplicitType() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}