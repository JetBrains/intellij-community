// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase

class ImplicitClassHighlightingTest : LightJavaCodeInsightFixtureTestCase() {
  override fun getProjectDescriptor() = JAVA_21
  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass"

  fun testHighlightInsufficientLevel() {
    IdeaTestUtil.withLevel(module, LanguageLevel.JDK_20, Runnable {
      doTest()
    })
  }

  fun testWithPackageStatement() {
    doTest()
  }

  fun testStaticInitializer() {
    doTest()
  }

  fun testHashCodeInMethod() {
    doTest()
  }

  fun `testIncorrect implicit class name with spaces`() {
    doTest()
  }

  fun testNestedReferenceHighlighting() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}