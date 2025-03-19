// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.openapi.components.service
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.NeedsIndex
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import junit.framework.TestCase

private const val SMART_MODE_REASON_MESSAGE = "Logger completion is not supported in the dumb mode"

class LoggerCompletionTest : LightFixtureCompletionTestCase() {
  override fun getProjectDescriptor(): LightProjectDescriptor = JAVA_17

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testSlf4j() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLog4j2() {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLog4j() {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testApacheCommons() {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testMultipleLoggers() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(2, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLoggerAlreadyExistsSimple() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLoggerAlreadyExistsInheritance() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLoggerAlreadyExistsNestedClasses() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testLoggerAlreadyExistsNestedClassesWithInheritance() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testStaticQualifierWithLocalVariable() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(2, "log", "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
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

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testRespectCustomLoggerName() {
    val state = project.service<JvmLoggingSettingsStorage>().state
    val oldLoggerName = state.loggerName
    try {
      JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
      val newName = "NameLogger"
      state.loggerName = newName
      doTest(0, newName, "Name", "NamedParameterSpec", "NavigableMap")
    }
    finally {
      state.loggerName = oldLoggerName
    }
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNoAutoCompletionInClassBody() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("long", "protected Object clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNoAutoCompletionAfterMethodInvocation() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNoAutoCompletionInBinaryExpression() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionAfterSemicolon() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSwitchStatement() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSwitchExpression() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionAfterLBrace() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionAfterRBrace() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSingleIfStatement() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSingleWhileStatement() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSingleForStatement() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInSingleForEachStatement() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testAutoCompletionInLambdaExpression() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNoAutoCompletionAfterReferenceExpression() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("logMethod", "anotherLogMethod")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  fun testNoAutoCompletionAfterNewExpression() {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("logMethod", "anotherLogMethod", "clone")
  }

  override fun getBasePath() = JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/completion/logger"


  private fun doAntiTest(vararg names: String) {
    val name = getTestName(true)
    configureByFile("$name.java")
    assertStringItems(*names)
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