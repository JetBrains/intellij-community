// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection

import com.intellij.JavaTestUtil
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.codeInspection.duplicateExpressions.DuplicateExpressionsInspection
import com.intellij.java.JavaBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.JAVA_11
import com.intellij.testFramework.fixtures.lightJavaCodeInsightFixture
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.ui.ChooserInterceptor
import com.intellij.ui.UiInterceptors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.regex.Pattern
import kotlin.time.Duration.Companion.minutes

@TestApplication
class DuplicateExpressionsFixTest {
  companion object {
    private val projectFixture = lightJavaProjectFixture(JAVA_11)
  }

  private val myFixture by lightJavaCodeInsightFixture(projectFixture)

  @BeforeEach
  fun setUpTestDataPath() {
    myFixture.testDataPath = JavaTestUtil.getJavaTestDataPath() + "/inspection/duplicateExpressionsFix"
  }

  val inspection = DuplicateExpressionsInspection()

  @BeforeEach
  fun setUp() {
    myFixture.enableInspections(inspection)
  }

  @Test
  fun testIntroduceVariable(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    UiInterceptors.register(ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")))
    doTest(introduce("s.substring(s.length() - 9)"))
  }
  @Test
  fun testReuseVariable(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest(reuse("substr", "s.substring(s.length() - 9)")) }
  @Test
  fun testReplaceOthers(): Unit = timeoutRunBlocking(timeout = 2.minutes) { doTest(replace("substr", "s.substring(s.length() - 9)")) }
  @Test
  fun testIntroduceVariableOtherVariableNotInScope(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    UiInterceptors.register(ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")))
    doTest(introduce("s.substring(s.length() - 9)"))
  }
  @Test
  fun testVariableNotInScopeCantReplaceOthers(): Unit = doNegativeTest(replace("substr", "s.substring(s.length() - 9)"))
  @Test
  fun testIntroducePathVariableTwoPathOf(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    UiInterceptors.register(ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")))
    doTest(introduce("Path.of(fileName)"))
  }
  @Test
  fun testIntroducePathVariableTwoPathsGet(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    UiInterceptors.register(ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")))
    doTest(introduce("Paths.get(fileName)"))
  }
  @Test
  fun testIntroducePathVariablePathOfPathsGet(): Unit = timeoutRunBlocking(timeout = 2.minutes) {
    UiInterceptors.register(ChooserInterceptor(null, Pattern.quote("Replace all 0 occurrences")))
    doTest(introduce("Paths.get(fileName)"))
  }
  @Test
  fun testRandomUUIDIsNotReplaced() {
    doNegativeTest(replace("s1", "String.valueOf(UUID.randomUUID().getMostSignificantBits())"))
  }

  private suspend fun doTest(message: String, threshold: Int = 50) =
    withThreshold(threshold) {
      myFixture.configureByFile("${myFixture.getTestName(false)}.java")
      withContext(Dispatchers.EDT) {
        myFixture.launchAction(myFixture.findSingleIntention(message))
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion()
        myFixture.checkResultByFile("${myFixture.getTestName(false)}_after.java")
      }
    }

  private fun doNegativeTest(message: String, threshold: Int = 50) =
    withThreshold(threshold) {
      myFixture.configureByFile("${myFixture.getTestName(false)}.java")
      val intentions = myFixture.filterAvailableIntentions(message)
      assertEquals(emptyList<IntentionAction>(), intentions)
    }

  private inline fun withThreshold(threshold: Int, block: () -> Unit) {
    val oldThreshold = inspection.complexityThreshold
    try {
      inspection.complexityThreshold = threshold
      block()
    }
    finally {
      inspection.complexityThreshold = oldThreshold
    }
  }

  private fun introduce(expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.introduce.variable.fix.name", expr)

  private fun reuse(name: String, expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.reuse.variable.fix.name", name, expr)

  private fun replace(name: String, expr: String) =
    JavaBundle.message("inspection.duplicate.expressions.replace.other.occurrences.fix.name", name, expr)
}
