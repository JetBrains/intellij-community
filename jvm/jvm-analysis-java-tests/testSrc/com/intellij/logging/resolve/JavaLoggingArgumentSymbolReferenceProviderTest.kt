package com.intellij.logging.resolve

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingArgumentSymbolReferenceProviderTestBase
import com.intellij.openapi.util.TextRange

class JavaLoggingArgumentSymbolReferenceProviderTest : LoggingArgumentSymbolReferenceProviderTestBase() {
  fun `test log4j2 info`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 debug`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.debug("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 trace`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.trace("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 fatal`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.fatal("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 error`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.error("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 log`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.log(Level.ALL, "<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 formatted logger`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int i) {
          LOG.info("<caret>%d", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 default logger builder`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.atInfo().log("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 formatted logger builder`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().log("<caret>%d", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test log4j2 respects exception with multiple arguments`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("<caret>{} {}", i, new Exception());
        }
     }
      """.trimIndent())

    doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "new Exception()"))
  }

  fun `test log4j2 respects exception as single argument`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("<caret>{}", new Exception()); 
        }
     }
      """.trimIndent())

    doTest(emptyMap())
  }

  fun `test log4j2 respects exception in builder logger with multiple arguments`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.atInfo().log("<caret>{} {}", i, new Exception());
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "new Exception()"))
  }

  fun `test log4j2 respects exception in builder logger as single argument`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.atInfo().log("<caret>{}", new Exception());
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "new Exception()"))
  }

  fun `test slf4j info`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test slf4j debug`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.debug("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test slf4j error`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.error("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test slf4j warn`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.warn("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }


  fun `test slf4j trace`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.trace("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }


  fun `test slf4j builder simple`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().log("<caret>{}", i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "i"))
  }

  fun `test slf4j builder with setMessage`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().addArgument("foo").setMessage("<caret>{} {}").addArgument("bar").log();
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  fun `test slf4j builder considers only last message`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().addArgument("foo").setMessage("<caret>{} {} {}").setMessage("{} {}").addArgument("bar").log();
        }
     }
      """.trimIndent())
    doTest(emptyMap())
  }

  fun `test slf4j builder combined args`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().addArgument("foo").addArgument("bar").log("<caret>{} {} {}", "baz");
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\"", TextRange(7, 9) to "\"baz\""))
  }

  fun `test slf4j builder respects last argument as exception in log`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().addArgument("foo").addArgument("bar").log("<caret>{} {} {}", new Throwable());
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  fun `test slf4j builder respects last argument as exception in setCause`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.atInfo().addArgument("foo").addArgument("bar").setCause(new Throwable()).log("<caret>{} {} {}");
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  fun `test should resolve in multiline string`() {
    val multilineString = "\"\"\"\n" +
                          "<caret>{}\n" +
                          "\"\"\""
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info($multilineString, i);
        }
     }
      """.trimIndent())
    doTest(mapOf(TextRange(4, 6) to "i"))
  }

  fun `test should not resolve with string concatenation`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info("{} <caret>" +  "}", i);
        }
     }
      """.trimIndent())
    doTest(emptyMap())
  }
}