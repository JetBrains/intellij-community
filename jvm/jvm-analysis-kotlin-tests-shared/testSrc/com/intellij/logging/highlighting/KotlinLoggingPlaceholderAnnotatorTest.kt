package com.intellij.logging.highlighting

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderAnnotatorTestBase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingPlaceholderAnnotatorTest : LoggingPlaceholderAnnotatorTestBase(), KotlinPluginModeProvider {
  override val fileName: String = "Logging.kt"

  fun `test log4j2 default log`() = doTest(
    """
       import org.apache.logging.log4j.*
       class Logging {
           val LOG: Logger = LogManager.getLogger()
           fun m(fst: Int, snd: Int) {
               LOG.debug("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.trace("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.warn("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.error("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.fatal("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
               LOG.log(Level.ALL, "<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
           }
       }""")

  fun `test log4j2 formatter log`() = doTest("""
      import org.apache.logging.log4j.*
      class Logging {
          val LOG: Logger = LogManager.getFormatterLogger()
          fun m(fst: Int, snd: Int) {
              LOG.debug("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.info("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.trace("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.warn("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.error("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.fatal("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.log(Level.ALL, "<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
          }
      }
    """.trimIndent())

  fun `test log4j2 default log builder`() = doTest("""
      import org.apache.logging.log4j.*
      class Logging {
          val LOG: Logger = LogManager.getLogger()
          fun m(fst: Int, snd: Int) {
              LOG.atDebug().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atWarn().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atError().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atFatal().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atTrace().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atLevel(Level.ALL).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
          }
      }
    """.trimIndent())

  fun `test log4j2 formatter log builder`() = doTest("""
      import org.apache.logging.log4j.*
      class Logging {
          val LOG: Logger = LogManager.getFormatterLogger()
          fun m(fst: Int, snd: Int) {
              LOG.atDebug().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atInfo().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atWarn().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atError().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atFatal().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atTrace().log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
              LOG.atLevel(Level.ALL).log("<placeholder>%s</placeholder> <placeholder>%s</placeholder>", fst, snd)
          }
      }
    """.trimIndent())


  fun `test log4j2 formatter log with previous placeholder`() = doTest("""
    import org.apache.logging.log4j.*
      class Logging {
          val LOG: Logger = LogManager.getFormatterLogger()
          fun m(fst: Int, snd: Int) {
              LOG.atDebug().log("<placeholder>%s</placeholder> <placeholder>%<s</placeholder>", fst)
              LOG.debug("<placeholder>%s</placeholder> <placeholder>%<s</placeholder>", fst)
          }
      }
    ""${'"'}.trimIndent())
  """.trimIndent())

  fun `test log4j2 respects exception`() = doTest("""
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", i, Exception())
          LOG.info("{}", Exception())
        }
     }
    """.trimIndent())

  fun `test log4j2 builder respects exception`() = doTest("""
      import org.apache.logging.log4j.*
      class Logging {
        val LOG: Logger = LogManager.getLogger()
        fun m(i: Int) {
          LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", i, Exception())
          LOG.atInfo().log("<placeholder>{}</placeholder>", Exception())
          LOG.atInfo().withThrowable(Exception()).log("{}")
        }
     }
      """.trimIndent())


  fun `test slf4j default log`() = doTest("""
      import org.slf4j.*

      class Logging {
          val LOG = LoggerFactory.getLogger(Logging::class.java)
          fun m(fst: Int, snd: Int) {
              LOG.debug("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.info("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.trace("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.warn("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.error("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
          }
      }
    """.trimIndent())

  fun `test slf4j default log builder`() = doTest("""
      import org.slf4j.*
      
      class Logging {
          val LOG = LoggerFactory.getLogger(Logging::class.java)
          fun m(fst: Int, snd: Int) {
              LOG.atInfo().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atDebug().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atWarn().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atError().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
              LOG.atTrace().log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", fst, snd)
          }
      }
    """.trimIndent())

  fun `test slf4j default log builder with setMessage and addArgument`() = doTest("""
      import org.slf4j.*

      class Logging {
          val LOG = LoggerFactory.getLogger(Logging::class.java)
          fun m(fst: Int, snd: Int) {
              LOG.atInfo().addArgument(fst).addArgument(snd).setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log()
              LOG.atInfo().addArgument(fst).addArgument(snd).setMessage("{} {}").setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log()
              LOG.atInfo().addArgument(fst).addArgument(snd).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>")
              LOG.atInfo().addArgument(fst).log("<placeholder>{}</placeholder> <placeholder>{}</placeholder>", snd)
          }
      }
    """.trimIndent())


  fun `test slf4j respects exception`() = doTest("""
      import org.slf4j.*

      class Logging {
          val LOG = LoggerFactory.getLogger(Logging::class.java)
          fun m(fst: Int, snd: Int) {
              LOG.info("<placeholder>{}</placeholder> {}", fst, Exception())
              LOG.info("{}", Exception())
          }
      }
    """.trimIndent())


  fun `test slf4j builder respects exception`() = doTest(
    """
      import org.slf4j.*

      class Logging {
          val LOG = LoggerFactory.getLogger(Logging::class.java)
          fun m(fst: Int, snd: Int) {
              LOG.atInfo().log("<placeholder>{}</placeholder> {}", fst, Exception())
              LOG.atInfo().log("{}", Exception())
              LOG.atInfo().addArgument(fst).addArgument(Exception()).setMessage("<placeholder>{}</placeholder> <placeholder>{}</placeholder>").log()
              LOG.atInfo().addArgument(fst).setCause(Exception()).setMessage("<placeholder>{}</placeholder> {}").log()
              LOG.atInfo().addArgument(fst).setCause(Exception()).log("<placeholder>{}</placeholder> {}")
          }
      }
    """.trimIndent()
  )

  fun `test support multiline string`() {
    val multilineString = "\"\"\"       \n" +
                          "    <placeholder>{}</placeholder> <placeholder>{}</placeholder> {}\n  \n" +
                          "      \n \n  \"\"\""

    doTest("""
    import org.slf4j.*

    class Logging {
        val LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(fst: Int, snd: Int) {
            LOG.info($multilineString, fst, snd)
        }
    }
  """.trimIndent())
  }

  fun `test does not support string concatenation`() = doTest("""
    import org.slf4j.*

    class Logging {
        val LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(fst: Int, snd: Int) {
            LOG.info("{} {}" + "  foo bar", fst, snd)
        }
    }
  """.trimIndent())

  fun `test does not support string interpolation`() {
    val dollar = "$"
    doTest("""
    import org.slf4j.*

    class Logging {
        val LOG = LoggerFactory.getLogger(Logging::class.java)
        fun m(fst: Int, snd: Int) {
            LOG.info("{} {} ${dollar}LOG", fst, snd)
        }
    }
  """.trimIndent())
  }
}