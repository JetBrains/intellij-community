// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.openapi.components.service
import com.intellij.testFramework.NeedsIndex
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import junit.framework.TestCase

class LoggerCompletionTest : LightFixtureCompletionTestCase() {
  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testMultipleLoggers() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(2, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLoggerAlreadyExistsSimple() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLoggerAlreadyExistsInheritance() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLoggerAlreadyExistsNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testLoggerAlreadyExistsNestedClassesWithInheritance() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testStaticQualifierWithLocalVariable() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(2, "log", "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
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

  @NeedsIndex.SmartMode(reason = "Logger completion is not supported in the dumb mode")
  fun testRespectCustomLoggerName() {
    val state = project.service<JvmLoggingSettingsStorage>().state
    val oldLoggerName = state.loggerName
    try {
      JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
      val newName = "NameLogger"
      state.loggerName = newName
      doTest(0, newName, "NavigableMap", "NegativeArraySizeException", "NoSuchAlgorithmException", "Runnable")
    }
    finally {
      state.loggerName = oldLoggerName
    }
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/logger"

  override fun doAntiTest() {
    val name = getTestName(true)
    configureByFile("$name.java")
    assertStringItems("log", "long", "clone")
    TestCase.assertFalse(
      lookup.items.any {
        it is JvmLoggerLookupElement
      }
    )
  }

  private fun doTest(position: Int, vararg names: String) {
    val name = getTestName(false)
    configureByFile("before$name.java")
    assertStringItems(*names)

    val item = lookup.items[position]
    selectItem(item)
    checkResultByFile("after$name.java")
  }
}