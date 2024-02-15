// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import junit.framework.TestCase

class LoggerCompletionTest : LightFixtureCompletionTestCase() {
  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(1, "long", "log", "clone")
  }

  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest(1, "long", "log", "clone")
  }

  fun testNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  fun testMultipleLoggers() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(2, "long", "log", "log", "clone")
  }

  fun testLoggerAlreadyExists() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    val name = getTestName(false)
    configureByFile("$name.java")
    assertStringItems("log", "long", "clone")

    TestCase.assertFalse(
      lookup.items.any {
        it is JvmLoggerLookupElement
      }
    )
  }

  fun testStaticQualifierWithLocalVariable() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(2, "log", "long", "log", "clone")
  }

  fun testAutoCompletionAfterDot() {
    var isAutoComplete = true
    try {
      isAutoComplete = CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars
      CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars = true
      JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
      val name = getTestName(false)
      configureByFile("before$name.java")

      myFixture.type(".")
      checkResultByFile("after$name.java")
    }
    finally {
      CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars = isAutoComplete
    }
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/logger"

  private fun doTest(position: Int, vararg names: String) {
    val name = getTestName(false)
    configureByFile("before$name.java")
    assertStringItems(*names)

    val item = lookup.items[position]
    selectItem(item)
    checkResultByFile("after$name.java")
  }
}