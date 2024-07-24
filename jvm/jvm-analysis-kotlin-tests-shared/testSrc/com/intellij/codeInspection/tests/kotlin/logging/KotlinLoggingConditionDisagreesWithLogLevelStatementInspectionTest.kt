package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingConditionDisagreesWithLogLevelStatementInspectionTest : LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase(), KotlinPluginModeProvider {
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

  fun `test several logs`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory

      internal object X {
          private val logger = LoggerFactory.getLogger()
          fun test1() {
              if (logger.isDebugEnabled) {
                  try {
                      something()
                      logger.debug("a")
                  } catch (e: Exception) {
                      logger.error("a")
                  }
              }
              if (logger.isDebugEnabled()) {
                  try {
                      something()
                      logger.debug("a")
                  } catch (e: Exception) {
                      logger.error("a")
                  }
              }
              if (logger.<warning descr="Level of condition 'INFO' does not match level of logging call 'DEBUG'"><warning descr="Level of condition 'INFO' does not match level of logging call 'ERROR'">isInfoEnabled</warning></warning>) {
                  try {
                      something()
                      logger.debug("a")
                  } catch (e: Exception) {
                      logger.error("a")
                  }
              }
              if (logger.<warning descr="Level of condition 'INFO' does not match level of logging call 'DEBUG'"><warning descr="Level of condition 'INFO' does not match level of logging call 'ERROR'">isInfoEnabled</warning></warning>()) {
                  try {
                      something()
                      logger.debug("a")
                  } catch (e: Exception) {
                      logger.error("a")
                  }
              }
          }

          private fun something() {}
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

  fun `test fixes slf4j change guards`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal object Slf4J {
          private val log: Logger = LoggerFactory.getLogger(Slf4J::class.java)
      
          private fun request1(i: String) {
              val msg = "log messages2: {}"
              if (log.isDeb<caret>ugEnabled) {
                  log.info(msg, i)
              }
          }
      }""".trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal object Slf4J {
          private val log: Logger = LoggerFactory.getLogger(Slf4J::class.java)
      
          private fun request1(i: String) {
              val msg = "log messages2: {}"
              if (log.isInfoEnabled) {
                  log.info(msg, i)
              }
          }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name", 0)
    )
  }

  fun `test fixes slf4j change guards as methods`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal object Slf4J {
          private val log: Logger = LoggerFactory.getLogger(Slf4J::class.java)
      
          private fun request1(i: String) {
              val msg = "log messages2: {}"
              if (log.isDeb<caret>ugEnabled()) {
                  log.info(msg, i)
              }
          }
      }""".trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal object Slf4J {
          private val log: Logger = LoggerFactory.getLogger(Slf4J::class.java)
      
          private fun request1(i: String) {
              val msg = "log messages2: {}"
              if (log.isInfoEnabled()) {
                  log.info(msg, i)
              }
          }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name", 0)
    )
  }
}