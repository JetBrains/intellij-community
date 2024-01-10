package com.intellij.codeInspection.tests.java.logging

import com.intellij.java.JavaBundle
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingSimilarMessageInspectionTestBase
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils
import com.intellij.jvm.analysis.testFramework.JvmLanguage

class JavaLoggingSimilarMessageInspectionTest : LoggingSimilarMessageInspectionTestBase() {

  fun `test equals log4j2`() {
    myFixture.testHighlighting(JvmLanguage.JAVA, """
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
    val availableIntention = myFixture.getAvailableIntention(JavaBundle.message("navigate.to.duplicate.fix"))
    assertNotNull(availableIntention)
    availableIntention?.invoke(project, editor, file)
    val offset = editor.caretModel.offset
    assertEquals(321, offset)
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
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
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
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
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
          .setMessage("aaa {}")
          .log()</weak_warning>;
      
          <weak_warning descr="Similar log messages">logger.atError()
          .setMessage("aaa 2{}")
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
        }
    
        private static void request2(int i) {
            String msg = "log messages: " + i;
            <weak_warning descr="Similar log messages">LOG.info(msg)</weak_warning>;
        }
     }
    """.trimIndent())
  }
}

