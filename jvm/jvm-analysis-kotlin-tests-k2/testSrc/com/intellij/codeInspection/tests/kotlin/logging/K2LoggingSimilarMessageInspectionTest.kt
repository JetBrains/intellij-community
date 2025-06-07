package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2LoggingSimilarMessageInspectionTest : KotlinLoggingSimilarMessageInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2

  fun `test equals slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
      
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
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
      
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
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
      
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
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
      
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


  fun `test suppressed slf4j statement`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.Logger
        import org.slf4j.LoggerFactory
        
        internal object Logging {
            private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
        
            private fun request1(i: String) {
                LOG.debug("Call successful")
            }
        
            fun test2() {
                @Suppress("LoggingSimilarMessage")
                LOG.debug("Call successful")
            }
        }
    """.trimIndent())
  }

  fun `test suppressed slf4j method`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal object Logging {
          private val LOG: Logger = LoggerFactory.getLogger(Logging::class.<error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'java'.">java</error>)
      
          private fun request1(i: String) {
              LOG.debug("Call successful")
          }
      
          @Suppress("LoggingSimilarMessage")
          fun test2() {
              LOG.debug("Call successful")
          }
      }
    """.trimIndent())
  }
}

