package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingSimilarMessageInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingSimilarMessageInspectionTest : LoggingSimilarMessageInspectionTestBase(), KotlinPluginModeProvider {

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

  fun `test equals slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: java">java</error>)
      
          private fun request1(i: String) {
              val msg = "log messages: {}"
              LOG.<weak_warning descr="Similar log messages">info(msg, i)</weak_warning>
              LOG.info("1" + msg, i)
          }
      
          private fun request2(i: Int) {
              val msg = "log messages: {}"
              LOG.<weak_warning descr="Similar log messages">info(msg, i)</weak_warning>
          }
      }
    """.trimIndent())
  }

  fun `test setMessage slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: java">java</error>)
      
          private fun request1(i: String) {
              val msg = "log messages: {}" + i
              LOG.atInfo().setCause(RuntimeException()).setMessage(msg).log()
               LOG.atInfo().setMessage(msg).<weak_warning descr="Similar log messages">log()</weak_warning>
          }
      
          private fun request2(i: Int) {
              val msg = "log messages: {}" + i
              LOG.atInfo().setCause(RuntimeException()).setMessage(msg).log()
               LOG.atInfo().setMessage(msg).<weak_warning descr="Similar log messages">log()</weak_warning>
          }
      }
    """.trimIndent())
  }

  fun `test skip in the middle parts`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: java">java</error>)
      
          private fun request1(i: String) {
              val msg = "${"\${i}"}1234356${"\${i}"}"
              LOG.info(msg)
          }
      
          private fun request2(i: Int) {
              val msg = "something 1234356${"\${i}"}"
              LOG.info(msg)
          }
      }
    """.trimIndent())
  }

  fun `test skip inside calls`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference: java">java</error>)
      
          private fun request2() {
            LOG.warn("Non-cached operation ${"\${operationName(\"update\")}"}")
            LOG.warn("Non-cached operation ${"\${operationName(\"getChildren\")}"}")
          }
          
          private fun operationName(operationName: String): String {
            return operationName
          }
      }
    """.trimIndent())
  }
}

