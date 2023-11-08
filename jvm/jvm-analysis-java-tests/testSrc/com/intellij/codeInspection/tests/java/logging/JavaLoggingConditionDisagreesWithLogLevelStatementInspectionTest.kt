package com.intellij.codeInspection.tests.java.logging

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
}