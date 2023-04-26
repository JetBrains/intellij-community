package com.intellij.codeInspection.tests.java.logging

import com.intellij.codeInspection.tests.JvmLanguage
import com.intellij.codeInspection.tests.logging.LoggingConditionDisagreesWithLogLevelStatementInspectionTestBase

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