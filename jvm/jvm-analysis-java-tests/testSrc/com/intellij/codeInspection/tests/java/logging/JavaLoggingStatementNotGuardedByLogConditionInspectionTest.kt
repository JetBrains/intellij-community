package com.intellij.codeInspection.tests.java.logging

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingStatementNotGuardedByLogConditionInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaLoggingStatementNotGuardedByLogConditionInspectionTest : LoggingStatementNotGuardedByLogConditionInspectionTestBase() {
  fun `test slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
        <warning descr="Logging call not guarded by a logging condition">LOG.debug("test" + arg)</warning>;
        }
      }
    """.trimIndent())
  }


  fun `test inside lambda slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          Runnable r = ()->LOG.debug("test" + arg);
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
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test" + arg)</warning>;
        }
      }
    """.trimIndent())
  }

  fun `test skip according level for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          LOG.warn("test" + arg);
        }
      }
    """.trimIndent())
  }

  fun `test skip according level for custom logger`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.logging.*;
      class X {
        static final Logger LOG = Logger.getLogger("");
        void n(String arg) {
          LOG.warning("test" + arg);
        }
      }
    """.trimIndent())
  }

  fun `test is surrounded by guard for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if((LOG.isDebugEnabled())) {
            LOG.debug("test" + arg);          
          }
          
          if(LOG.isInfoEnabled()) {
            <warning descr="Logging call not guarded by a logging condition">LOG.debug("test" + arg)</warning>; //todo!          
          }
          
          if(true && LOG.isDebugEnabled()) {
            LOG.debug("test" + arg);          
          }
          
          if(true && LOG.isDebugEnabled()) {
            if(true) {
              LOG.debug("test" + arg);          
            }          
          }
          
          if(true) {
            if(true) {
              <warning descr="Logging call not guarded by a logging condition">LOG.debug("test" + arg)</warning>;          
            }          
          }
        }
      }
    """.trimIndent())
  }

  fun `test is surrounded by guard for custom logger`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import java.util.logging.*;
      class X {
        static final Logger LOG = Logger.getLogger("");
        void n(String arg) {
          if(LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("test" + arg);          
          }
          
          if(true && LOG.isLoggable(java.util.logging.Level.FINE)) {
            LOG.fine("test" + arg);          
          }          
        }
      }
    """.trimIndent())
  }

  fun `test skip if only constant arguments for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          LOG.debug("test");
          LOG.debug("test {} {}", "test" + "test", 1 + 1);
        }
      }
    """.trimIndent())
  }

  fun `test don't skip if only constant arguments for slf4j flagUnguardedConstant`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n1(String arg) {
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test")</warning>;
        }
        void n2(String arg) {
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test")</warning>;
        }
      }
    """.trimIndent())
  }

  fun `test skip with several log calls for slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n2(String arg) {
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>;
          LOG.debug("test2" + arg);
        }

        void n3(String arg) {
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>;
          LOG.debug("test2" + arg);
          LOG.debug("test2" + arg);
        }
        
        void constantCall(String arg) {
          LOG.debug("test1");
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test2" + arg)</warning>;
        }
        
        void beforeNotLog(String arg) {
          constantCall(arg);
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test2" + arg)</warning>;
        }

        void differentLevels(String arg) {
          <warning descr="Logging call not guarded by a logging condition">LOG.debug("test1" + arg)</warning>;
          LOG.warn("test2" + arg);
        }
      }
    """.trimIndent())
  }

  fun `test lambda`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.apache.logging.log4j.*;
      class X {
        static final Logger LOG = LogManager.getLogger();
        void n(String arg) {
          LOG.info("test {}", ()->"1");
        }
      }
    """.trimIndent())
  }

  fun `test fix simple slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          LOG.<caret>debug("test" + arg);
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("test" + arg);
            }
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix simple nested slf4j`() {
    myFixture.testQuickFix(
      testPreview = false,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          if(true){
            LOG.<caret>debug("test" + arg);          
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
            if(true){
                if (LOG.isDebugEnabled()) {
                    LOG.debug("test" + arg);
                }
            }
          }
        }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix several similar slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          LOG<caret>.debug("test1" + arg);
          LOG.debug("test2" + arg);
          LOG.debug("test3" + arg);
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("test1" + arg);
                LOG.debug("test2" + arg);
                LOG.debug("test3" + arg);
            }
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }

  fun `test fix several different slf4j`() {
    myFixture.testQuickFix(
      testPreview = true,
      lang = JvmLanguage.JAVA,
      before = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
          LOG<caret>.debug("test1" + arg);
          LOG.debug("test2" + arg);
          LOG.trace("test3" + arg);
        }
      }
      """.trimIndent(),
      after = """
      import org.slf4j.Logger;
      import org.slf4j.LoggerFactory;
      class X {
        private static final Logger LOG = LoggerFactory.getLogger(X.class);
        void n(String arg) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("test1" + arg);
                LOG.debug("test2" + arg);
            }
            LOG.trace("test3" + arg);
        }
      }
      """.trimIndent(),
      hint = JvmAnalysisBundle.message("jvm.inspection.log.statement.not.guarded.log.fix.family.name")
    )
  }
}