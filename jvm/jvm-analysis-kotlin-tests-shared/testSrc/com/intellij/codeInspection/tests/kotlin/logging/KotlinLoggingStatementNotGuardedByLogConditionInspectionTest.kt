package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingStatementNotGuardedByLogConditionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingStatementNotGuardedByLogConditionInspectionTest : LoggingStatementNotGuardedByLogConditionInspectionTestBase(), KotlinPluginModeProvider {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          private val LOG = LoggerFactory.getLogger()
      
          fun n(arg: String) {
            <warning descr="Logging call not guarded by a logging condition">LOG.debug("test {}", arg)</warning>
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
            <warning descr="Logging call not guarded by a logging condition">LOG.debug("test {}", arg)</warning>
          }
      
          companion object {
              val LOG: Logger = LogManager.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test skip according level for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              LOG.warn("test " + arg)
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test is surrounded by guard for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n(arg: String) {
              if (LOG.isDebugEnabled) {
                  LOG.debug("test " + arg)
              }
      
              if (LOG.isInfoEnabled) {
                <warning descr="Logging call not guarded by a logging condition">LOG.debug("test" + arg)</warning> //todo!          
              }
      
              if (true && LOG.isDebugEnabled) {
                  LOG.debug("test {}", arg)
              }
      
              if (true && LOG.isDebugEnabled) {
                  if (true) {
                      LOG.debug("test {}", arg)
                  }
              }
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test skip if only constant arguments for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n() {
              LOG.debug("test")
              LOG.debug("test {} {}", "test" + "test", 1 + 1)
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test don't skip if only constant arguments for slf4j flagUnguardedConstant`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n1() {
            <warning descr="Logging call not guarded by a logging condition">LOG.debug("test")</warning>
          }
      
          fun n2() {
            <warning descr="Logging call not guarded by a logging condition">LOG.debug("test")</warning>
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test skip with several log calls for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.Logger
      import org.slf4j.LoggerFactory
      
      internal class X {
          fun n2(arg: String) {
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>
              LOG.debug("test2" + arg)
          }
      
          fun n3(arg: String) {
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>
              LOG.debug("test2" + arg)
              LOG.debug("test2" + arg)
          }
      
          fun constantCall(arg: String) {
              LOG.debug("test1")
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test2" + arg)</warning>
          }
      
          fun beforeNotLog(arg: String) {
              constantCall(arg)
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test2" + arg)</warning>
          }
      
          fun differentLevels(arg: String) {
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>
              LOG.warn("test2" + arg)
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
    """.trimIndent())
  }

  fun `test fix simple slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
        import org.slf4j.Logger
        import org.slf4j.LoggerFactory
        
        internal class X {
            fun n(arg: String) {
                LOG.<caret>debug("test" + arg)
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
                if (LOG.isDebugEnabled) {
                    LOG.debug("test" + arg)
                }
            }
        
            companion object {
                private val LOG: Logger = LoggerFactory.getLogger()
            }
        }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix simple nested slf4j`() {
    myFixture.testQuickFix(
      testPreview = false,
      lang = JvmLanguage.KOTLIN,
      before = """
        import org.slf4j.Logger
        import org.slf4j.LoggerFactory
        
        internal class X {
            fun n(arg: String) {
                if(true){
                    LOG.<caret>debug("test" + arg)                
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
                if(true){
                    if (LOG.isDebugEnabled) {
                        LOG.debug("test" + arg)
                    }                
                }
            }

            companion object {
                private val LOG: Logger = LoggerFactory.getLogger()
            }
        }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix several similar slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
        import org.slf4j.Logger
        import org.slf4j.LoggerFactory
        
        internal class X {
            fun n(arg: String) {
                LOG.<caret>debug("test1" + arg)
                LOG.debug("test2" + arg)
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
                if (LOG.isDebugEnabled) {
                    LOG.debug("test1" + arg)
                    LOG.debug("test2" + arg)
                }
            }
        
            companion object {
                private val LOG: Logger = LoggerFactory.getLogger()
            }
        }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix several different slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.KOTLIN,
      before = """
        import org.slf4j.Logger
        import org.slf4j.LoggerFactory
        
        internal class X {
            fun n(arg: String) {
                LOG.<caret>debug("test1" + arg)
                LOG.debug("test2" + arg)
                LOG.info("test3" + arg)
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
              if (LOG.isDebugEnabled) {
                  LOG.debug("test1" + arg)
                  LOG.debug("test2" + arg)
              }
              LOG.info("test3" + arg)
          }
      
          companion object {
              private val LOG: Logger = LoggerFactory.getLogger()
          }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }
}