// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.JavaTestUtil
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_21
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class MoveMembersFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_21)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/implicitClass"
  }

  @Test
  fun testHighlightInsufficientLevel() {
    IdeaTestUtil.withLevel(myFixture.module, LanguageLevel.JDK_20, Runnable {
      doTest()
    })
  }

  @Test
  fun testWithPackageStatement() {
    doTest()
  }

  @Test
  fun testStaticInitializer() {
    doTest()
  }

  @Test
  fun testHashCodeInMethod() {
    doTest()
  }

  private fun doTest() {
    myFixture.configureByFile(myFixture.getTestName(false) + ".java")
    myFixture.checkHighlighting()
  }
}