package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingSimilarMessageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.test.ExpectedPluginModeProvider
import org.jetbrains.kotlin.idea.test.setUpWithKotlinPlugin

abstract class KotlinLoggingSimilarMessageInspectionTest : LoggingSimilarMessageInspectionTestBase(), ExpectedPluginModeProvider {
  override fun setUp() {
    setUpWithKotlinPlugin(testRootDisposable) { super.setUp() }
  }

  fun `test equals log4j2`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger
      
      internal class Logging {
          private val logger: Logger = LogManager.getLogger()
      
          private fun request1(i: String) {
              val msg = "log messages: {}"
              logger.<weak_warning descr="Similar log messages">info(msg, i)</weak_warning>
          }
      
          private fun request2(i: Int) {
              val msg = "log messages: {}"
              logger.<weak_warning descr="Similar log messages">info(msg, i)</weak_warning>
          }
      }
    """.trimIndent())
  }
}

