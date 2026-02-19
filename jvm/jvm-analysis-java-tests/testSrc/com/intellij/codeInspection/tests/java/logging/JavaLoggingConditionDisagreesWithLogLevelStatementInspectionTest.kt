package com.intellij.codeInspection.tests.java.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaLoggingConditionDisagreesWithLogLevelStatementInspectionTest : LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase() {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n() {
          if (LOG.isInfoEnabled()) {
            LOG.info("nothing to report");
          }
          if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>()) {
            LOG.warn("test");
          }
        }
      }
    """.trimIndent())
  }

  fun `test double guarded`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        private static void test2() {
            if (LOG.isDebugEnabled()) {
                try {
                    something();
                    if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'DEBUG'">isInfoEnabled</warning>()) {
                        if (problem()) {
                            LOG.debug("test3");
                        }
                    }
                } catch (Exception ignore) {
                }
            }
        }
    
        private static boolean something() {
            return false;
        }        
        private static boolean problem() {
            return false;
        }
      }
    """.trimIndent())
  }

  fun `test several logs`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger logger = LoggerFactory.getLogger(X.class);
        public static void test1() {
            if (logger.isDebugEnabled()) {
                try {
                    something();
                    logger.debug("a");
                } catch (Exception e) {
                    logger.error("a");
                }
            }
        }
        private static void something() {
        }
      }
    """.trimIndent())
  }
  fun `test several logs 2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger logger = LoggerFactory.getLogger(X.class);
        public static void test1() {
            if (logger.<warning descr="Level of condition 'DEBUG' does not match level of logging call 'ERROR'"><warning descr="Level of condition 'DEBUG' does not match level of logging call 'INFO'">isDebugEnabled</warning></warning>()) {
                try {
                    something();
                    logger.info("a");
                } catch (Exception e) {
                    logger.error("a");
                }
            }
        }
        private static void something() {
        }
      }
    """.trimIndent())
  }

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class X {
        static final Logger LOG = LogManager.getLogger();
        void m() {
          if (LOG.<warning descr="Level of condition 'INFO' does not match level of logging call 'WARN'">isInfoEnabled</warning>()) {
            LOG.warn("test");
          }
        }
      }
    """.trimIndent())
  }

  fun `test java util logging`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.logging.*;
      class Loggers {
        static final Logger LOG = Logger.getLogger("");
        public void method() {
          if (LOG.<warning descr="Level of condition 'FINE' does not match level of logging call 'WARNING'">isLoggable</warning>(Level.FINE)) {
            LOG.warning("test");
          }
          if (LOG.isLoggable(Level.WARNING)) {
            LOG.warning("not error");
          }
        }
      }
      """.trimIndent())
  }

  fun `test fixes log4j2 change calls`() {
      myFixture.testQuickFix(
        testPreview = true,
        lang = JvmLanguage.JAVA,
        before = """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class Logging {
          private static final Logger LOG = LogManager.getLogger();
      
          private static void request1(String i) {
              String msg = "log messages2: {}";
              if (LOG.is<caret>DebugEnabled()) {
                  LOG.info(msg, i);
              }
          }
      }""".trimIndent(),
        after = """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class Logging {
          private static final Logger LOG = LogManager.getLogger();
      
          private static void request1(String i) {
              String msg = "log messages2: {}";
              if (LOG.isDebugEnabled()) {
                  LOG.debug(msg, i);
              }
          }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name", 1)
      )
  }

  fun `test fixes log4j2 change guards`() {
      myFixture.testQuickFix(
        testPreview = true,
        lang = JvmLanguage.JAVA,
        before = """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class Logging {
          private static final Logger LOG = LogManager.getLogger();
      
          private static void request1(String i) {
              String msg = "log messages2: {}";
              if (LOG.is<caret>DebugEnabled()) {
                  LOG.info(msg, i);
              }
          }
      }""".trimIndent(),
        after = """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class Logging {
          private static final Logger LOG = LogManager.getLogger();
      
          private static void request1(String i) {
              String msg = "log messages2: {}";
              if (LOG.isInfoEnabled()) {
                  LOG.info(msg, i);
              }
          }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name", 0)
      )
  }

  fun `test fixes slf4j change guards`() {
      myFixture.testQuickFix(
        testPreview = true,
        lang = JvmLanguage.JAVA,
        before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Slf4J {
        private final static Logger log = LoggerFactory.getLogger(Slf4J.class);
        
        private static void request1(String i) {
            String msg = "log messages2: {}";
            if (log.is<caret>DebugEnabled()) {
                log.info(msg, i);
            }
        }
      }""".trimIndent(),
        after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      
      class Slf4J {
        private final static Logger log = LoggerFactory.getLogger(Slf4J.class);
        
        private static void request1(String i) {
            String msg = "log messages2: {}";
            if (log.isInfoEnabled()) {
                log.info(msg, i);
            }
        }
      }""".trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.logging.condition.disagrees.with.log.statement.fix.name", 0)
      )
  }
}