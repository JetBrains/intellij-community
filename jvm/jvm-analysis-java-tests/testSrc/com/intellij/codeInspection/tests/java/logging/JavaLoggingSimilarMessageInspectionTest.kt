package com.intellij.codeInspection.tests.java.logging

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.java.JavaBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils
import com.intellij.jvm.analysis.internal.testFramework.logging.loggingSimilarMessageInspectionFixture
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import com.intellij.jvm.analysis.testFramework.jvmProjectFixture
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

@TestApplication
class JavaLoggingSimilarMessageInspectionTest {
  companion object {
    private val projectFixture = jvmProjectFixture()
  }

  private val myFixture by loggingSimilarMessageInspectionFixture(projectFixture)

  @Test
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
    runInEdtAndWait {
      val availableIntention = myFixture.getAvailableIntention(JavaBundle.message("navigate.to.duplicate.fix"))
      assertNotNull(availableIntention)
      availableIntention?.invoke(myFixture.project, myFixture.editor, myFixture.file)
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
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
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


  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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

  @Test
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


  @Test
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

  @Test
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


  @Test
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

  @Test
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

  @Test
  fun `test virtual file is null`() {
    val psiFile = myFixture.configureByText("${myFixture.generateFileName()}${JvmLanguage.JAVA.ext}", """
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
    runReadActionBlocking {
      val similarMessageInspection = myFixture.inspection
      val copy = psiFile.copy()
      val copyFile = copy as PsiFile
      assertNull(copyFile.virtualFile)
      val textRange = TextRange(0, copyFile.fileDocument.charsSequence.length)
      val visitor = similarMessageInspection.buildVisitor(
        holder = ProblemsHolder(InspectionManager.getInstance(myFixture.project), copyFile, true),
        isOnTheFly = true,
        session = LocalInspectionToolSession(copyFile, textRange, textRange, null))
      assertFalse(visitor == PsiElementVisitor.EMPTY_VISITOR)
    }
  }
}

