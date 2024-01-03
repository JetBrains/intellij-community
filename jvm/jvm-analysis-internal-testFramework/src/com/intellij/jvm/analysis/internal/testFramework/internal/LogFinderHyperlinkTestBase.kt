package com.intellij.jvm.analysis.internal.testFramework.internal

import com.intellij.analysis.customization.console.ClassLoggingConsoleFilterProvider
import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import org.intellij.lang.annotations.Language
import org.junit.Assert

class LogItem(val text: String, val position: LogicalPosition?)

abstract class LogFinderHyperlinkTestBase : LightJvmCodeInsightFixtureTestCase() {

  protected fun checkColumnFinderJava(@Language("Java") classText: String,
                                      fileName: String,
                                      logItems: List<LogItem>) {
    myFixture.configureByText("$fileName.java", classText)
    checkInside(classText, logItems)
  }

  protected fun checkColumnFinderKotlin(@Language("kotlin") classText: String,
                                        fileName: String,
                                        logItems: List<LogItem>) {
    myFixture.configureByText("$fileName.kt", classText)
    checkInside(classText, logItems)
  }

  private fun checkInside(classText: String,
                          logItems: List<LogItem>) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting("process.console.output.to.find.class.names", true, testRootDisposable)
    val editor = myFixture.editor
    Assert.assertEquals(classText, editor.document.text)
    val filters = ClassLoggingConsoleFilterProvider().getDefaultFilters(project)
    assertSize(1, filters)
    val filter = filters.first()
    var len = 0
    for (logItem in logItems) {
      val logLine = logItem.text
      len += logLine.length
      val result = filter.applyFilter(logLine, len)
      if (logItem.position == null) {
        Assert.assertNull(logItem.toString(), result)
        continue
      }
      Assert.assertNotNull(logItem.toString(), result)
      val info = result!!.firstHyperlinkInfo
      Assert.assertNotNull(logItem.toString(), info)
      info!!.navigate(project)
      val actualPos = editor.caretModel.logicalPosition
      Assert.assertEquals(logItem.toString(), logItem.position, actualPos)
    }
  }
}