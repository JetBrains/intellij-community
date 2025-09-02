package com.intellij.codeInspection.tests.java.logging

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.java.JavaBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingSimilarMessageInspectionTestBase
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class JavaLoggingSimilarMessageInspectionTest : LoggingSimilarMessageInspectionTestBase() {

  fun `test equals log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log messages: "  + i;
            <weak_warning descr="Similar log messages">L<caret>OG.info(msg)</weak_warning>;
            <weak_warning descr="Similar log messages">LOG.error(msg)</weak_warning>;
            LOG.error(msg, new RuntimeException());
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
            <weak_warning descr="Similar log messages">LOG.error(msg)</weak_warning>;
            LOG.error(msg, new RuntimeException());
        }
     }
    """.trimIndent())
    val availableIntention = myFixture.getAvailableIntention(JavaBundle.message("navigate.to.duplicate.fix"))
    assertNotNull(availableIntention)
    availableIntention?.invoke(project, editor, file)
    myFixture.checkResult("""
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log messages: "  + i;
            LOG.info(msg);
            LOG.error(msg);
            LOG.error(msg, new RuntimeException());
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <selection><caret>LOG.info(msg)</selection>;
            LOG.error(msg);
            LOG.error(msg, new RuntimeException());
        }
     }
    """.trimIndent())
  }

  fun `test not completed log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "{}"  + i;
            LOG.info(msg);
        }
    
        private static void request2(int i) {
            String msg = "{}" + i;
            LOG.info(msg);
        }
     }
    """.trimIndent())
  }

  fun `test not completed 2 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "{} {}";
            LOG.info(msg);
        }
    
        private static void request2(int i) {
            String msg = "{} {}";
            LOG.info(msg);
        }
     }
    """.trimIndent())
  }

  fun `test many equals log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log messages2: "  + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request3(int i) {
            String msg = "log messages2: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request4(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request5(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request6(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request7(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request8(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request9(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request10(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);
        
        private static void request1(String i) {
            String msg = "log messages: "  + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
            <weak_warning descr="Similar log messages">LOG.error(msg)</weak_warning>;
            LOG.error(msg, new RuntimeException());
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
            <weak_warning descr="Similar log messages">LOG.error(msg)</weak_warning>;
            LOG.error(msg, new RuntimeException());
        }
     }
    """.trimIndent())
  }


  fun `test setMessage equals slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);
        
        private static void request1(String i) {
            String msg = "log messages: "  + i;
            <weak_warning descr="Similar log messages">LOG.atInfo().setMessage(msg).log()</weak_warning>;
            LOG.atInfo().setCause(new RuntimeException("1234")).setMessage(msg).log();
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.atInfo().setMessage(msg).log()</weak_warning>;
            LOG.atInfo().setCause(new RuntimeException("1234")).setMessage(msg).log();
        }
     }
    """.trimIndent())
  }

  fun `test not equals level slf4j`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      import org.slf4j.spi.*;
      class X {
    
        void foo() {
          Logger logger = LoggerFactory.getLogger(X.class);
                  
          <weak_warning descr="Similar log messages">logger.atError()
          .setMessage("aaaaa {}")
          .log()</weak_warning>;
        }        
        void foo2() {
          Logger logger = LoggerFactory.getLogger(X.class);
                  
          <weak_warning descr="Similar log messages">logger.atError()
          .setMessage("aaaaa 2{}")
          .log()</weak_warning>;
        }        
      }
      """.trimIndent())
  }

  fun `test not equals log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log 2 messages: "  + i;
            LOG.info(msg);
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            LOG.info(msg);
        }
     }
    """.trimIndent())
  }

  fun `test not equals end log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log 2 messages";
            LOG.info(msg);
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages";
            LOG.info(msg);
        }
     }
    """.trimIndent())
  }

  fun `test endWith log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + "2: log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test endWith2 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + "2: log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test startWith log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log messages" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = "log messages2" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test startWith 2 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = "log messages2" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = "log messages" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals end log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test many equals end log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request3(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request4(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request5(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request6(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request7(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request8(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        private static void request9(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
        
        private static void request10(int i) {
            String msg = i + ": log messages";
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals middle log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals middle contains log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages: 2" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals middle contains 2 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages: 2" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals middle contains 3 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + "2: log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + ": log messages:" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test equals middle contains 4 log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages:" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = i + "2: log messages:" + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }

  fun `test not equals middle log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            String msg = i + ": log messages: " + i;
            LOG.info(msg);
        }
    
        private static void request2(int i) {
            String msg = i + ": log 2 messages: " + i;
            LOG.info(msg);
        }
     }
    """.trimIndent())
  }

  fun `test equals log4j2 with placeholders`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            <weak_warning descr="Similar log messages">LOG.info("log messages: ", i)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info("log messages: ", i)</weak_warning>;
        }
     }
    """.trimIndent())
  }


  fun `test equals idea`() {
    LoggingTestUtils.addIdeaLog(myFixture)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     class Logging {        
        private static com.intellij.openapi.diagnostic.Logger LOG = null;
        
        private static void request1(String i) {
            String msg = "log messages: "  + i;
            <weak_warning descr="Similar log messages">L<caret>OG.info(msg)</weak_warning>;
            LOG.error(msg);
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">L<caret>OG.info(msg)</weak_warning>;
            LOG.error(msg);
        }
     }
    """.trimIndent())
  }

  fun `test idea with additional info`() {
    LoggingTestUtils.addIdeaLog(myFixture)
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     class Logging {        
        private static com.intellij.openapi.diagnostic.Logger LOG = null;
        
        private static void request1(String i) {
            String msg = "log messages: "  + i + ")";
            LOG.info(msg);
            LOG.error(msg);
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i + "something" + i +")";
            LOG.info(msg);
            LOG.error(msg);
        }
     }
    """.trimIndent())
  }


  fun `test sequence`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        private static void request1(String i) {
            LOG.info("testtesttest");
            LOG.info("testtesttest");
            if(LOG.isInfoEnabled()){
               LOG.info("testtesttest");
            }
            if(LOG.isInfoEnabled()) LOG.info("testtesttest");
        }
     }
    """.trimIndent())
  }

  fun `test non-distinguished`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
     import org.apache.logging.log4j.*;
     class Logging {
        private static final Logger LOG = LogManager.getLogger();
        
        public static void m() {
            LOG.info("Hello World");
        }
        
        public static void m1() {
            LOG.info("Hello World: test new");
        }
     }
    """.trimIndent())
  }

  fun `test suppressed slf4j statement`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);
        
        private static void request1(String i) {
          LOG.debug("Call successful");
        }
    
        private static void request2(int i) {
          //noinspection LoggingSimilarMessage
          LOG.debug("Call successful");
        }
     }
    """.trimIndent())
  }

  fun `test suppressed slf4j method`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
      import org.slf4j.*;
      class Logging {
        private static Logger LOG = LoggerFactory.getLogger(Logging.class);
        
        private static void request1(String i) {
          LOG.debug("Call successful");
        }
    
        @SuppressWarnings("LoggingSimilarMessage")
        public static void test2() {
            LOG.debug("Call successful");
        }
     }
    """.trimIndent())
  }

  fun `test virtual file is null`() {
    val psiFile = myFixture.configureByText("${generateFileName()}${JvmLanguage.JAVA.ext}", """
       import org.apache.logging.log4j.*;
       class Logging {
          private static final Logger LOG = LogManager.getLogger();
          
          private static void request1(String i) {
              String msg = "log messages: "  + i;
              <weak_warning descr="Similar log messages">L<caret>OG.info(msg)</weak_warning>;
          }
      
          private static void request2(int i) {
              String msg = "log messages: " + i;
              <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
          }
       }
      """.trimIndent())
    val similarMessageInspection = inspection
    val copy = psiFile.copy()
    val copyFile = copy as PsiFile
    assertNull(copyFile.virtualFile)
    val textRange = TextRange(0, copyFile.fileDocument.charsSequence.length)
    val visitor = similarMessageInspection.buildVisitor(
      holder = ProblemsHolder(InspectionManager.getInstance(project), copyFile, true),
      isOnTheFly = true,
      session = LocalInspectionToolSession(copyFile, textRange, textRange, null))
    assertFalse(visitor == PsiElementVisitor.EMPTY_VISITOR)
  }
}

