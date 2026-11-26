// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.Companion.lightDaemonAnalyzerFixture
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.fixtures.lightJavaTestDescriptor
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class SemicolonHighlightingTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor())
  }

  private val daemon by lightDaemonAnalyzerFixture(projectFixture)

  private val BASE_PATH = "/codeInsight/daemonCodeAnalyzer/semicolonHighlighting"

  @BeforeEach
  fun setUp() {
    daemon.setLanguageLevel(LanguageLevel.JDK_21)
  }

  private fun doTest() {
    daemon.doTest(BASE_PATH + "/" + daemon.fixture.getTestName(false) + ".java", false, false)
  }

  @Test fun testSemicolonAfterImport() { doTest() }
  @Test fun testSemicolonBeforeImport() { doTest() }
  @Test fun testSemicolonBetweenImport() { doTest() }
  @Test fun testSemicolonWithoutImport() { doTest() }
}