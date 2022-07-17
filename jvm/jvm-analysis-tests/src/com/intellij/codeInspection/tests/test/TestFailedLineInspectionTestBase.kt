package com.intellij.codeInspection.tests.test

import com.intellij.codeInspection.test.TestFailedLineInspection
import com.intellij.codeInspection.tests.UastInspectionTestBase
import com.intellij.execution.TestStateStorage
import com.intellij.execution.testframework.JavaTestLocator
import com.intellij.execution.testframework.sm.runner.states.TestStateInfo
import com.intellij.execution.testframework.sm.runner.ui.TestStackTraceParser
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.markup.EffectType
import java.util.*

abstract class TestFailedLineInspectionTestBase : UastInspectionTestBase() {
  override val inspection = TestFailedLineInspection()

  override fun setUp() {
    super.setUp()
    myFixture.addClass("package junit.framework; public class TestCase {}")
  }

  protected fun doTest(fileName: String, fileExt: String, methodName: String, errorLn: Int, errorMessage: String) {
    val url = "java:test://$fileName/$methodName"
    val pair = TestStackTraceParser(url, """	
      |${'\t'}at junit.framework.Assert.fail(Assert.java:47)
      |${'\t'}at $fileName.assertEquals(Assert.java:207)
      |${'\t'}at $fileName.$methodName($fileName.$fileExt:$errorLn)""".trimMargin(), errorMessage, JavaTestLocator.INSTANCE, project)
    assertEquals(errorLn, pair.failedLine)
    assertEquals("assertEquals", pair.failedMethodName)
    val record = TestStateStorage.Record(
      TestStateInfo.Magnitude.FAILED_INDEX.value, Date(), 0,
      pair.failedLine, pair.failedMethodName, pair.errorMessage, pair.topLocationLine
    )
    TestStateStorage.getInstance(project).writeState(url, record)
    myFixture.testHighlighting("$fileName.$fileExt")
    val infos = myFixture.doHighlighting(HighlightSeverity.WARNING)
    assertEquals(1, infos.size)
    val attributes = infos.first().forcedTextAttributes
    assertNotNull(attributes)
    assertEquals(EffectType.BOLD_DOTTED_LINE, attributes.effectType)
  }
}