package com.intellij.jvm.analysis.internal.testFramework.internal

import com.intellij.analysis.customization.console.ClassLoggingConsoleFilterProvider
import com.intellij.analysis.customization.console.OnFlyMultipleFilesHyperlinkInfo
import com.intellij.analysis.customization.console.SLF4J_MAVEN
import com.intellij.jvm.analysis.testFramework.LightJvmCodeInsightFixtureTestCase
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import org.intellij.lang.annotations.Language
import org.junit.Assert

class LogItem(val text: String, val position: LogicalPosition?)

abstract class LogFinderHyperlinkTestBase : LightJvmCodeInsightFixtureTestCase() {

  override fun getProjectDescriptor(): LightProjectDescriptor = object: ProjectDescriptor(LanguageLevel.HIGHEST){
    override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
      super.configureModule(module, model, contentEntry)
      model.addSlf4jApi()
    }

    private fun ModifiableRootModel.addSlf4jApi() {
      MavenDependencyUtil.addFromMaven(this, "$SLF4J_MAVEN:2.0.12")
    }
  }

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
        if (result == null) {
          continue
        }
        val hyperlinkInfo = result.firstHyperlinkInfo
        if (hyperlinkInfo is OnFlyMultipleFilesHyperlinkInfo) {
          val files = hyperlinkInfo.getFiles(project)
          Assert.assertTrue(logItem.toString(), files.isEmpty())
        }
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