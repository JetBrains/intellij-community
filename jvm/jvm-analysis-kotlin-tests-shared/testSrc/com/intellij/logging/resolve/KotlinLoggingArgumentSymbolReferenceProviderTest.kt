package com.intellij.logging.resolve

import com.intellij.jvm.analysis.internal.testFramework.logging.loggingArgumentSymbolReferenceProviderFixture
import com.intellij.jvm.analysis.internal.testFramework.logging.loggingArgumentSymbolReferenceProviderProjectFixture
import com.intellij.openapi.util.TextRange
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Test

@TestApplication
abstract class KotlinLoggingArgumentSymbolReferenceProviderTest {
  companion object {
    private val projectFixture = loggingArgumentSymbolReferenceProviderProjectFixture()
  }

  protected val myFixture by loggingArgumentSymbolReferenceProviderFixture(projectFixture)

  @Test
  fun `test log4j2 info`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.info("<caret>{}", i)
        }
      }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 debug`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.debug("<caret>{}", i)
        }
      }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 trace`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.trace("<caret>{}", i)
        }
      }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 fatal`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.fatal("<caret>{}", i)
        }
      }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 error`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.fatal("<caret>{}", i)
        }
      }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 log`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.log(Level.ALL, "<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 formatted logger`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getFormatterLogger()
        fun m(i: Int) {
          LOG.info("<caret>%d", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders simple`() {
    val dollar = "$"
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG = LogManager.getFormatterLogger()
        fun m(fst: Int, snd: Int) {
          LOG.info("<caret>%2${dollar}s %1${dollar}s", fst, snd)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders same index`() {
    val dollar = "$"
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG = LogManager.getFormatterLogger()
        fun m(fst: Int, snd: Int) {
          LOG.info("<caret>%1${dollar}s %1${dollar}s", fst)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test log4j2 formatted logger with numbered placeholders mix`() {
    val dollar = "$"
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG = LogManager.getFormatterLogger()
        fun m(fst: Int, snd: Int) {
          LOG.info("<caret>%2${dollar}s %1${dollar}s %s %s", fst, snd)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test log4j2 formatted logger with previous placeholder simple`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG = LogManager.getFormatterLogger()
        fun m(fst: Int, snd: Int) {
          LOG.info("<caret>%s %<s %<s", fst)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "fst", TextRange(4, 7) to "fst", TextRange(8, 11) to "fst"))
  }

  @Test
  fun `test log4j2 formatted logger with previous placeholder mix`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getFormatterLogger()
        
        fun m(fst: Int, snd: Int) {
          LOG.info("<caret>%s %<s %<s %s %<s %<s", fst, snd)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "fst", TextRange(4, 7) to "fst", TextRange(8, 11) to "fst",
                 TextRange(12, 14) to "snd", TextRange(15, 18) to "snd", TextRange(19, 22) to "snd"))
  }

  @Test
  fun `test log4j2 formatted logger with numbered and previous placeholder`() {
    val dollar = "$"
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG = LogManager.getFormatterLogger()
        fun m(fst: Int) {
          LOG.info("<caret>%1${dollar}s %<s", fst)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }


  @Test
  fun `test log4j2 default logger builder`() {
    myFixture.configureByText("Logging.kt",
                              """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.atInfo().log("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 formatted logger builder`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getFormatterLogger()
        fun m(i: Int) {
          LOG.atInfo().log("<caret>%d", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test log4j2 respects exception with multiple arguments`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.info("<caret>{} {}", i, Exception())
        }
     }
      """.trimIndent())

    myFixture.doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "Exception()"))
  }

  @Test
  fun `test log4j2 respects exception as single argument`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.info("<caret>{}", Exception()) 
        }
     }
      """.trimIndent())

    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test log4j2 respects exception in builder logger with multiple arguments`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.atInfo().log("<caret>{} {}", i, Exception())
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i", TextRange(4, 6) to "Exception()"))
  }

  @Test
  fun `test log4j2 respects exception in builder logger as single argument`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.atInfo().log("<caret>{}", Exception())
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "Exception()"))
  }

  @Test
  fun `test slf4j info`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.info("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test slf4j debug`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.debug("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test slf4j error`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.error("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test slf4j warn`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.warn("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }


  @Test
  fun `test slf4j trace`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.trace("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }


  @Test
  fun `test slf4j builder simple`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().log("<caret>{}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "i"))
  }

  @Test
  fun `test slf4j builder with setMessage`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().addArgument("foo").setMessage("<caret>{} {}").addArgument("bar").log()
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  @Test
  fun `test slf4j builder considers only last message`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().addArgument("foo").setMessage("<caret>{} {} {}").setMessage("{} {}").addArgument("bar").log()
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test slf4j builder combined args`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().addArgument("foo").addArgument("bar").log("<caret>{} {} {}", "baz")
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\"", TextRange(7, 9) to "\"baz\""))
  }

  @Test
  fun `test slf4j builder respects last argument as exception in log`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().addArgument("foo").addArgument("bar").log("<caret>{} {} {}", Throwable())
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "\"foo\"", TextRange(4, 6) to "\"bar\""))
  }

  @Test
  fun `test slf4j builder respects last argument as exception in setCause`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.atInfo().addArgument("foo").addArgument("bar").setCause(Throwable()).log("<caret>{} {} {}")
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
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging::class.java)
        fun m(i: Int) {
          LOG.info($multilineString, i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(4, 6) to "i"))
  }

  @Test
  fun `test should resolve in strange multiline string`() {
    val strangeMultilineString = "\"\"\"\n " +
                                 "              <caret>{}   \n    " +
                                 "     {}       " +
                                 "  \"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(i: Int) {
          LOG.info($strangeMultilineString, i, i)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(14, 16) to "i", TextRange(24, 26) to "i"))
  }

  @Test
  fun `test should not resolve in multiline string with brace on next line`() {
    val multilineString = "\"\"\"\n" +
                          "<caret>{\n}" +
                          "\"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val Logger LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(i: Int) {
          LOG.info($multilineString, i)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }


  @Test
  fun `test should not resolve with interpolation`() {
    val dollar = "$"
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(i: Int) {
          LOG.info("${dollar}i {} <caret> {}", i, i)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test should not resolve with string concatenation`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.info("{} <caret>" +  "}", i)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test resolve with escape characters in simple string log4j2`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger(Logging.class)
        fun m(i: Int) {
           LOG.info("\"{} \'{<caret>} \${"$"}{} \t{} \b{} \n{} \r{} \\{}", 1, 2, 3, 4, 5, 6, 7, 8)
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
                          "\\\"{} \\'{<caret>} \\t{} \\b{} \\n{} \\r{} \\\\{}" +
                          "\"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.info($multilineString, 1, 2, 3, 4, 5, 6, 7)
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
  fun `test should not resolve with consecutive escape characters in simple string log4j2`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.info("\s\\{<caret>}", 1)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(5, 7) to "1"))
  }

  @Test
  fun `test should not resolve with consecutive escape characters in multiline string log4j2`() {
    val multilineString = "\"\"\"\n" +
                          "\\s\\{<caret>}" +
                          "\"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger(Logging.class)
        fun m(i: Int) {
          LOG.info($multilineString, 1)
        }
     }
      """.trimIndent())
    myFixture.doTest(mapOf(TextRange(7, 9) to "1"))
  }

  @Test
  fun `test resolve with escape characters in simple string slf4j`() {
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger()
        fun m(i: Int) {
           LOG.info("\"{} \'{<caret>} \${"$"}{} \t{} \b{} \n{} \r{} \\{}", 1, 2, 3, 4, 5, 6, 7)
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
                          "\\\"{} \\'{<caret>} \\t{} \\b{} \\n{} \\r{} \\\\{}" +
                          "\"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger()
        fun m(i: Int) {
          LOG.info($multilineString, 1, 2, 3, 4, 5, 6, 7)
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
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger()
        fun m(i: Int) {
          LOG.info("\s\\{<caret>}", 1)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test should not resolve with consecutive escape characters in multiline string slf4j`() {
    val multilineString = "\"\"\"\n" +
                          "\\s\\{<caret>}" +
                          "\"\"\""
    myFixture.configureByText("Logging.kt", """
      import org.slf4j.*
      class Logging {
        val LOG: Logger = LoggerFactory.getLogger()
        fun m(i: Int) {
          LOG.info($multilineString, 1)
        }
     }
      """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test lazy init in init block`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger
      
      class Initialization {
          init {
              log.debug("{<caret>}", 1)
          }
      
          companion object {
              private val log : Logger
              init {
                  log = LogManager.getLogger()
              }
          }
      }
    """.trimIndent())
    myFixture.doTest(mapOf(TextRange(1, 3) to "1"))
  }

  @Test
  fun `test lazy init in init block with condition`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger
      
      class StaticInitializerBuilder2 {
        init {
            log.log("{<caret>}", 1)
        }
  
        companion object {
            private val log: LogBuilder
  
            init {
                if (1 == 1) {
                    log = LogManager.getLogger().atDebug()
                } else {
                    log = LogManager.getFormatterLogger().atDebug()
                }
            }
        }
    }
    """.trimIndent())
    myFixture.doTest(emptyMap())
  }

  @Test
  fun `test lazy init in constructors`() {
    myFixture.configureByText("Logging.kt", """
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger
      
       internal class ConstructorInitializer {
           private val log: Logger
  
           constructor() {
               log = LogManager.getLogger()
           }
  
           constructor(i) {
               log = LogManager.getLogger()
           }
  
           fun test() {
             log.info("{<caret>} {}", 1, 2)
           }
       }
    """.trimIndent())
    myFixture.doTest(mapOf(
      TextRange(1, 3) to "1",
      TextRange(4, 6) to "2",))
  }
}