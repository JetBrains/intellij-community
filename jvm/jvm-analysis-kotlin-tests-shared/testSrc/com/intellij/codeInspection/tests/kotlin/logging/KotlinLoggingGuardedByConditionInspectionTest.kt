package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingGuardedByConditionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingGuardedByConditionInspectionTest : LoggingGuardedByConditionInspectionTestBase(), KotlinPluginModeProvider {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<warning descr="Logging call guarded by log condition">isDebugEnabled</warning>) {
                  LOG.debug("test" + arg)
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<warning descr="Logging call guarded by log condition">isDebugEnabled()</warning>) {
                  LOG.debug("test1" + arg)
              }
          }
      
          companion object {
              val LOG: Logger = LogManager.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test several calls slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<warning descr="Logging call guarded by log condition">isDebugEnabled</warning>) {
                  LOG.debug("test1" + arg)
                  LOG.debug("test2" + arg)
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test several different calls slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<warning descr="Logging call guarded by log condition">isDebugEnabled</warning>) {
                  LOG.info("test1" + arg)
                  LOG.debug("test2" + arg)
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test several calls with another call slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<warning descr="Logging call guarded by log condition">isDebugEnabled</warning>) {
                  LOG.debug("test1" + arg)
                  arg.toString()
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
       }    
       """.trimIndent())
  }

  fun `test several calls with another call slf4j showOnlyIfFixPossible`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.isDebugEnabled) {
                  LOG.debug("test1" + arg)
                  arg.toString()
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
       }
    """.trimIndent())
  }

  fun `test slf4j fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<caret>isDebugEnabled) {
                  LOG.debug("test" + arg)
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              LOG.debug("test" + arg)
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test slf4j without block fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<caret>isDebugEnabled) 
                  LOG.debug("test" + arg)
              
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              LOG.debug("test" + arg)
              
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test several calls slf4j fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<caret>isDebugEnabled) {
                  LOG.debug("test1" + arg)
                  LOG.debug("test2" + arg)
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory

      internal class X {
          fun n(arg: String) {
              LOG.debug("test1" + arg)
              LOG.debug("test2" + arg)
          }

          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test slf4j with comment fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.<caret>isDebugEnabled) {//comment1
                  //comment2
                  LOG.debug("test" + arg)
                  //comment3
                  
                  //comment4
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              //comment1
              //comment2
              LOG.debug("test" + arg)
              //comment3
      
              //comment4
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }
}