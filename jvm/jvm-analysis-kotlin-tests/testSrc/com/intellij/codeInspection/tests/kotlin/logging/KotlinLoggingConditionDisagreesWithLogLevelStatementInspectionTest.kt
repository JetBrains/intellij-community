package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.logging.LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase

class KotlinLoggingConditionDisagreesWithLogLevelStatementInspectionTest : LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase() {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory

        internal class X {
            fun n() {
                if (LOG.isInfoEnabled) {
                    LOG.info("nothing to report")
                }
                if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>) {
                    LOG.warn("test")
                }
                if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>()) {
                    LOG.warn("test")
                }
            }
        
            companion object {
                private val LOG = LoggerFactory.getLogger()
            }
        }
    """.trimIndent())
  }

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.apache.logging.log4j.LogManager
      internal class Y {
          fun m() {
              if (LOG.isWarnEnabled()) {
                   LOG.warn("test")
              }
              if (LOG.isWarnEnabled  ) {
                   LOG.warn("test")
              }
              if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>  ) {
                   LOG.warn("test")
              }
              if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>()) {
                  LOG.warn("test")
              }
          }
      
          companion object {
              val LOG = LogManager.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test java util logging`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import java.util.logging.Level
      import java.util.logging.Logger
      
      val LOG = Logger.getLogger("")
      
      internal class Loggers {
          fun method() {
              if (LOG.<warning descr="Level of condition 'FINE' does not match level of logging call 'WARNING'">isLoggable</warning>(Level.FINE)) {
                  LOG.warning("test")
              }
              if (LOG.isLoggable(Level.WARNING)) {
                  LOG.warning("not error")
              }
          }
      }
    """.trimIndent())
  }
}