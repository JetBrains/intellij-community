// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase
import com.intellij.pom.java.LanguageLevel

class SemicolonHighlightingTest: LightDaemonAnalyzerTestCase() {
  private val BASE_PATH = "/codeInsight/daemonCodeAnalyzer/semicolonHighlighting"

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    setLanguageLevel(LanguageLevel.JDK_21)
  }

  private fun doTest() {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", false, false)
  }

  fun testSemicolonAfterImport() { doTest() }
  fun testSemicolonBeforeImport() { doTest() }
  fun testSemicolonBetweenImport() { doTest() }
  fun testSemicolonWithoutImport() { doTest() }
}