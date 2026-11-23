package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.loggingConditionDisagreesWithLogLevelStatementInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinLoggingConditionDisagreesWithLogLevelStatementInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  protected val myFixture by loggingConditionDisagreesWithLogLevelStatementInspectionFixture(projectFixture)

  

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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