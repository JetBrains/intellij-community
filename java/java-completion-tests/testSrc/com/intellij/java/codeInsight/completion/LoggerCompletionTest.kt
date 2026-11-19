// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.completion.JvmLoggerLookupElement
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.java.codeInsight.JvmLoggerTestSetupUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate
import com.intellij.testFramework.NeedsIndex
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_17
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

private const val SMART_MODE_REASON_MESSAGE = "Logger completion is not supported in the dumb mode"

@TestApplication
@IndexingModeTemplate
class LoggerCompletionTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture(JAVA_17)
  }

  private val completion by lightCompletionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/codeInsight/completion/logger")
  private val myFixture get() = completion.fixture

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testSlf4j(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLog4j2(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLog4j(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupLog4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testApacheCommons(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupApacheCommons(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNestedClasses(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testMultipleLoggers(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    JvmLoggerTestSetupUtil.setupLog4j2(myFixture)
    doTest(2, "long", "log", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLoggerAlreadyExistsSimple(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLoggerAlreadyExistsInheritance(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLoggerAlreadyExistsNestedClasses(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testLoggerAlreadyExistsNestedClassesWithInheritance(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("log", "long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testStaticQualifierWithLocalVariable(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(2, "log", "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionAfterDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    var isAutoComplete = true
    try {
      isAutoComplete = CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars
      CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars = true
      JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
      val name = myFixture.getTestName(false)
      completion.configureByFile("before$name.java")

      myFixture.type(".")
      completion.checkResultByFile("after$name.java")
    }
    finally {
      CodeInsightSettings.getInstance().isSelectAutopopupSuggestionsByChars = isAutoComplete
    }
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testRespectCustomLoggerName(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val state = myFixture.project.service<JvmLoggingSettingsStorage>().state
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
  @Test
  fun testNoAutoCompletionInClassBody(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("long", "protected Object clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionAfterMethodInvocation(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionInBinaryExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("long", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionAfterSemicolon(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSwitchStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSwitchExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionAfterLBrace(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionAfterRBrace(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSingleIfStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSingleWhileStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSingleForStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInSingleForEachStatement(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testAutoCompletionInLambdaExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doTest(1, "long", "log", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionAfterReferenceExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("logMethod", "anotherLogMethod")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionAfterNewExpression(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("logMethod", "anotherLogMethod", "clone")
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionAfterErrorElementWithDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest()
  }

  @NeedsIndex.SmartMode(reason = SMART_MODE_REASON_MESSAGE)
  @Test
  fun testNoAutoCompletionAfterExpressionWithDot(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    JvmLoggerTestSetupUtil.setupSlf4j(myFixture)
    doAntiTest("equals",
                 "hashCode",
                 "toString",
                 "getClass",
                 "notify",
                 "notifyAll",
                 "wait",
                 "wait",
                 "wait")
  }

  private fun doAntiTest(vararg names: String) {
    val name = myFixture.getTestName(true)
    completion.configureByFile("$name.java")
    completion.assertStringItems(*names)
    if (names.isEmpty()) {
      assertNull(completion.getLookup())
    }
    else {
      assertFalse(
        completion.getLookup()!!.items.any {
          it is JvmLoggerLookupElement
        }
      )
    }
  }

  private fun doTest(position: Int, vararg names: String) {
    val name = myFixture.getTestName(false)
    completion.configureByFile("before$name.java")
    completion.assertStringItems(*names)

    val item = completion.getLookup()!!.items[position]
    completion.selectItem(item)
    completion.checkResultByFile("after$name.java")
  }
}