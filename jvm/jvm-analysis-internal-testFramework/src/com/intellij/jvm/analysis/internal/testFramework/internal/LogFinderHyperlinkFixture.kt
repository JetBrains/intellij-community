// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.jvm.analysis.internal.testFramework.internal

import com.intellij.analysis.customization.console.ClassLoggingConsoleFilterProvider
import com.intellij.analysis.customization.console.OnFlyMultipleFilesHyperlinkInfo
import com.intellij.analysis.customization.console.SLF4J_MAVEN
import com.intellij.jvm.analysis.testFramework.JvmCodeInsightFixture
import com.intellij.jvm.analysis.testFramework.jvmCodeInsightFixture
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.roots.ContentEntry
import com.intellij.openapi.roots.ModifiableRootModel
import com.intellij.pom.java.LanguageLevel
import com.intellij.testFramework.awaitPendingNavigation
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase.ProjectDescriptor
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.fixtures.MavenDependencyUtil
import com.intellij.testFramework.fixtures.lightJavaProjectFixture
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.runInEdtAndWait
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.TestOnly
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * The fixture of a test of the class name links in the console. It is the JUnit 5 form of [LogFinderHyperlinkTestBase].
 * Create it with [logFinderHyperlinkFixture], and give it the project of [logFinderHyperlinkProjectFixture].
 */
@TestOnly
class LogFinderHyperlinkFixture internal constructor(base: JvmCodeInsightFixture) : JvmCodeInsightFixture(base.fixture) {
  /**
   * Configures the Java class [classText] in `<fileName>.java`, and checks the navigation of the links in [logItems]. It runs on the EDT.
   */
  fun checkColumnFinderJava(@Language("Java") classText: String, fileName: String, logItems: List<LogItem>) {
    runInEdtAndWait {
      configureByText("$fileName.java", classText)
      checkInside(classText, logItems)
    }
  }

  /**
   * Configures the Kotlin code [classText] in `<fileName>.kt`, and checks the navigation of the links in [logItems]. It runs on the EDT.
   */
  fun checkColumnFinderKotlin(@Language("kotlin") classText: String, fileName: String, logItems: List<LogItem>) {
    runInEdtAndWait {
      configureByText("$fileName.kt", classText)
      checkInside(classText, logItems)
    }
  }

  private fun checkInside(classText: String, logItems: List<LogItem>) {
    (AdvancedSettings.getInstance() as AdvancedSettingsImpl)
      .setSetting("process.console.output.to.find.class.names", true, testRootDisposable)
    val editor = editor
    assertEquals(classText, editor.document.text)
    val filters = ClassLoggingConsoleFilterProvider().getDefaultFilters(project)
    assertEquals(1, filters.size, filters.toList().toString())
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
          assertTrue(files.isEmpty(), logItem.toString())
        }
        continue
      }
      assertNotNull(result, logItem.toString())
      val info = result!!.firstHyperlinkInfo
      assertNotNull(info, logItem.toString())
      info!!.navigate(project)
      awaitPendingNavigation(project)
      val actualPos = editor.caretModel.logicalPosition
      assertEquals(logItem.position, actualPos, logItem.toString())
    }
  }
}

/**
 * Creates the project of a [LogFinderHyperlinkFixture] test class. It has the SLF4J API, as [LogFinderHyperlinkTestBase] has.
 */
@TestOnly
fun logFinderHyperlinkProjectFixture(): TestFixture<LightJavaProject> = lightJavaProjectFixture(object : ProjectDescriptor(LanguageLevel.HIGHEST) {
  override fun configureModule(module: Module, model: ModifiableRootModel, contentEntry: ContentEntry) {
    super.configureModule(module, model, contentEntry)
    MavenDependencyUtil.addFromMaven(model, "$SLF4J_MAVEN:2.0.12")
  }
})

/**
 * Creates a [LogFinderHyperlinkFixture] for one test. Give it the project of [logFinderHyperlinkProjectFixture].
 */
@TestOnly
fun logFinderHyperlinkFixture(projectFixture: TestFixture<LightJavaProject>): TestFixture<LogFinderHyperlinkFixture> =
  testFixture("logFinderHyperlinkFixture") {
    initialized(LogFinderHyperlinkFixture(jvmCodeInsightFixture(projectFixture).init())) {}
  }
