package com.intellij.codeInspection.tests.java.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingGuardedByConditionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaLoggingGuardedByConditionInspectionTest : LoggingGuardedByConditionInspectionTestBase() {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<warning descr="Logging call guarded by log condition">LOG.isDebugEnabled()</warning>) {
            LOG.debug("test" + arg);
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
        void n(String arg) {
          if(<warning descr="Logging call guarded by log condition">LOG.isDebugEnabled()</warning>) {
            LOG.debug("test1" + arg);          
          }        
        }
      }
    """.trimIndent())
  }

  fun `test several calls slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<warning descr="Logging call guarded by log condition">LOG.isDebugEnabled()</warning>) {
            LOG.debug("test1" + arg);
            LOG.debug("test2" + arg);
          }
        }
      }
    """.trimIndent())
  }

  fun `test several different calls slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<warning descr="Logging call guarded by log condition">LOG.isDebugEnabled()</warning>) {
            LOG.info("test1" + arg);
            LOG.debug("test2" + arg);
          }
        }
      }
    """.trimIndent())
  }

  fun `test several calls with another call slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<warning descr="Logging call guarded by log condition">LOG.isDebugEnabled()</warning>) {
            LOG.debug("test1" + arg);
            arg.toString();
          }
        }
      }
    """.trimIndent())
  }

  fun `test several calls with another call slf4j showOnlyIfFixPossible`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(LOG.isDebugEnabled()) {
            LOG.debug("test1" + arg);
            arg.toString();
          }
        }
      }
    """.trimIndent())
  }

  fun `test slf4j fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<caret>LOG.isDebugEnabled()) {
            LOG.debug("test" + arg);
          }
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            LOG.debug("test" + arg);
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test slf4j without block fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<caret>LOG.isDebugEnabled())
            LOG.debug("test" + arg);
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            LOG.debug("test" + arg);
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test several calls slf4j fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<caret>LOG.isDebugEnabled()) {
            LOG.debug("test1" + arg);
            LOG.debug("test2" + arg);
          }
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            LOG.debug("test1" + arg);
            LOG.debug("test2" + arg);
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }

  fun `test slf4j with comment fix`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(<caret>LOG.isDebugEnabled()) {//comment1
            //comment2
            LOG.debug("test" + arg);
            //comment3

            //comment4
          }
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            //comment1
            //comment2
            //comment3
            //comment4
            LOG.debug("test" + arg);
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.guarded.fix.family.name")
    )
  }
}