package com.intellij.logging.resolve

import com.intellij.jvm.analysis.internal.testFramework.logging.loggingArgumentSymbolReferenceProviderFixture
import com.intellij.jvm.analysis.internal.testFramework.logging.loggingArgumentSymbolReferenceProviderProjectFixture
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
class JavaLoggingArgumentSymbolReferenceProviderTest {
  companion object {
    private val projectFixture = loggingArgumentSymbolReferenceProviderProjectFixture()
  }

  private val myFixture by loggingArgumentSymbolReferenceProviderFixture(projectFixture)

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders simple`() {
    val dollar = "$"
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst, int snd) {
          LOG.info("<caret>%2${dollar}s %1${dollar}s", fst, snd);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 5) to "snd", TextRange(6, 10) to "fst"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders same index`() {
    val dollar = "$"
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst) {
          LOG.info("<caret>%1${dollar}s %1${dollar}s", fst);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 5) to "fst", TextRange(6, 10) to "fst"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders mix`() {
    val dollar = "$"
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst, int snd) {
          LOG.info("<caret>%2${dollar}s %1${dollar}s %s %s", fst, snd);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 5) to "snd", TextRange(6, 10) to "fst", TextRange(11, 13) to "fst", TextRange(14, 16) to "snd"))
  }

  @Test
  fun `test log4j2 formatted logger with previous placeholder simple`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst) {
          LOG.info("<caret>%s %<s %<s", fst);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "fst", TextRange(4, 7) to "fst", TextRange(8, 11) to "fst"))
  }

  @Test
  fun `test log4j2 formatted logger with previous placeholder mix`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst, int snd) {
          LOG.info("<caret>%s %<s %<s %s %<s %<s", fst, snd);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "fst", TextRange(4, 7) to "fst", TextRange(8, 11) to "fst",
                 TextRange(12, 14) to "snd", TextRange(15, 18) to "snd", TextRange(19, 22) to "snd"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered and previous placeholder`() {
    val dollar = "$"
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getFormatterLogger(Logging.class);
        void m(int fst) {
          LOG.info("<caret>%1${dollar}s %<s", fst);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 5) to "fst", TextRange(6, 9) to "fst"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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

    myFixture.doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "new Exception()"))
  }

  @Test
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

    myFixture.doTest(emptyMap())
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "new Exception()"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "new Exception()"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }


  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }


  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  @Test
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
    myFixture.doTest(emptyMap())
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\"", TextRange(7, 9) to "\"baz\""))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  @Test
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
    myFixture.doTest(mapOf(TextRange(4, 6) to "i"))
  }

  @Test
  fun `test should resolve in strange multiline string`() {
    val strangeMultilineString = "\"\"\"\n " +
                                 "              <caret>{}\n             " +
                                 "     {}       " +
                                 "  \"\"\""
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info($strangeMultilineString, i, i);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(14, 16) to "i", TextRange(30, 32) to "i"))
  }

  @Test
  fun `test should not resolve in multiline string with brace on next line`() {
    val multilineString = "\"\"\"\n" +
                          "<caret>{\n}" +
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
    myFixture.doTest(emptyMap())
  }

  @Test
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
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test resolve with escape characters in simple string log4j2`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("\"{} \'{<caret>} \f{} \t{} \b{} \n{} \r{} \\{}", 1, 2, 3, 4, 5, 6, 7, 8);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(3, 5) to "1",
      TextRange(8, 10) to "2",
      TextRange(13, 15) to "3",
      TextRange(18, 20) to "4",
      TextRange(23, 25) to "5",
      TextRange(28, 30) to "6",
      TextRange(33, 35) to "7",
      TextRange(38, 40) to "8",
    ))
  }

  @Test
  fun `test resolve with escape characters in multiline string log4j2`() {
    val multilineString = "\"\"\"\n" +
                          "\\\"{} \\'{<caret>} \\f{} \\t{} \\b{} \\n{} \\r{} \\\\{}" +
                          "\"\"\""
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info($multilineString, 1, 2, 3, 4, 5, 6, 7, 8);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(6, 8) to "1",
      TextRange(11, 13) to "2",
      TextRange(16, 18) to "3",
      TextRange(21, 23) to "4",
      TextRange(26, 28) to "5",
      TextRange(31, 33) to "6",
      TextRange(36, 38) to "7",
      TextRange(41, 43) to "8",
    ))
  }

  @Test
  fun `test should not resolve with consecutive escape characters in simple string log4j2`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info("\s\\{<caret>}", 1);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(5, 7) to "1"))
  }

  @Test
  fun `test should not resolve with consecutive escape characters in multiline string log4j2`() {
    val multilineString = "\"\"\"\n" +
                          "\\s\\\\{<caret>}" +
                          "\"\"\""
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.*;
      class Logging {
        private static final Logger LOG = LogManager.getLogger();
        void m(int i) {
          LOG.info($multilineString, 1);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(8, 10) to "1"))
  }

  @Test
  fun `test resolve with escape characters in simple string slf4j`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info("\"{} \'{<caret>} \f{} \t{} \b{} \n{} \r{} \\{}", 1, 2, 3, 4, 5, 6, 7);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(3, 5) to "1",
      TextRange(8, 10) to "2",
      TextRange(13, 15) to "3",
      TextRange(18, 20) to "4",
      TextRange(23, 25) to "5",
      TextRange(28, 30) to "6",
      TextRange(33, 35) to "7",
    ))
  }


  @Test
  fun `test resolve with escape characters in multiline string slf4j`() {
    val multilineString = "\"\"\"\n" +
                          "\\\"{} \\'{<caret>} \\f{} \\t{} \\b{} \\n{} \\r{} \\\\{}" +
                          "\"\"\""
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger(Logging.class);
        void m(int i) {
          LOG.info($multilineString, 1, 2, 3, 4, 5, 6, 7);
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(6, 8) to "1",
      TextRange(11, 13) to "2",
      TextRange(16, 18) to "3",
      TextRange(21, 23) to "4",
      TextRange(26, 28) to "5",
      TextRange(31, 33) to "6",
      TextRange(36, 38) to "7",
    ))
  }

  @Test
  fun `test should not resolve with consecutive escape characters in simple string slf4j`() {
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger();
        void m(int i) {
          LOG.info("\s\\{<caret>}", 1);
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test should not resolve with consecutive escape characters in multiline string slf4j`() {
    val multilineString = "\"\"\"\n" +
                          "\\s\\\\{<caret>}" +
                          "\"\"\""
    myFixture.configureByText("Logging.java", """
      import org.slf4j.*;
      class Logging {
        private static final Logger LOG = LoggerFactory.getLogger();
        void m(int i) {
          LOG.info($multilineString, 1);
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test lazy init in init block`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class StaticInitializer {
          private static final Logger log;
  
          static {
              log = LogManager.getLogger();
          }
      
          public StaticInitializer() {
              log.info("{<caret>} {}", 1, 2);
          }
      }
    """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(1, 3) to "1",
      TextRange(4, 6) to "2",))
  }

  @Test
  fun `test lazy init in constructors`() {
    myFixture.configureByText("Logging.java", """
      import org.apache.logging.log4j.LogManager;
      import org.apache.logging.log4j.Logger;
      
      class ConstructorInitializer {
          private final Logger log;
  
          public ConstructorInitializer() {
              log = LogManager.getLogger();
          }
  
          public ConstructorInitializer(int i) {
              log = LogManager.getLogger();
          }
  
          public void test() {
            log.info("{<caret>} {}", 1, 2);
          }
      }
    """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(1, 3) to "1",
      TextRange(4, 6) to "2",))
  }
}