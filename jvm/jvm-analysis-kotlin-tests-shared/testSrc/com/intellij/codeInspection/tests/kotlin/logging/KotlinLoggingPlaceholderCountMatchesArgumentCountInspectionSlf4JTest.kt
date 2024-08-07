package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionSlf4JTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase(), KotlinPluginModeProvider {
  fun `test slf4j disable slf4jToLog4J2Type`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory

        internal class X {
          fun foo(s: String) {
            val logger = LoggerFactory.getLogger()
            logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (2)">"string {} {}"</warning> , 1, RuntimeException())
            logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">s + "string {} {}"</warning> , 1, RuntimeException())
            logger.info(s + "string {} {}" , 1, 2)
            logger.atError().log( <warning descr="Fewer arguments provided (0) than placeholders specified (1)">"{}"</warning> , RuntimeException("test"))
            LoggerFactory.getLogger().atError().log( <warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning> , 1, RuntimeException("test"))
            LoggerFactory.getLogger().atError().log("{}", 1, RuntimeException("test"))
            LoggerFactory.getLogger().atError().log(s + "{}", 1, RuntimeException("test"))
            LoggerFactory.getLogger().atError().log(s + "{}", 1, 2)
            LoggerFactory.getLogger().atError().log(<warning descr="More arguments provided (1) than placeholders specified (0)">""</warning>, 1, RuntimeException("test"))
            LoggerFactory.getLogger().atError().log(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">s + "{} {} {}"</warning>, 1, RuntimeException("test"))
          }
        }
      """.trimIndent())
  }

  fun `test slf4j auto slf4jToLog4J2Type`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.AUTO
    myFixture.addClass("""
        package org.apache.logging.slf4j;
        public interface Log4jLogger {
        }
      """.trimIndent())

    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory
        
        internal class X {
            fun foo() {
                val logger = LoggerFactory.getLogger()
                logger.info("string {} {}", 1, RuntimeException())
                logger.atError().log("{}", RuntimeException("test"))
                LoggerFactory.getLogger().atError().log("{} {}", 1, RuntimeException("test"))
                LoggerFactory.getLogger().atError().log( <warning descr="More arguments provided (2) than placeholders specified (1)">"{}"</warning> , 1, RuntimeException("test"))
            }
        }
      """.trimIndent())
  }

  fun `test slf4j`() {
    inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO

    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.*;
        
        private val logger: Logger? = null
        private val brackets: String = "{}"
        
        fun foo() {
          logger?.debug(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.debug("test {} {}", *arrayOf(1, 2))
          logger?.debug("test {} {}", *arrayOf(1, 2, Exception()))
          logger?.debug(<warning descr="More arguments provided (2) than placeholders specified (1)">"test " + brackets</warning>, 1, 2) //warn
          logger?.debug("test {}" + brackets, 1, 2)
          logger?.debug("test {} {}", 1, 2)
          logger?.debug(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"test {} \\{} {}"</warning>, 1) //warn
          logger?.debug("test {} \\{} {}", 1, 2)
          logger?.debug(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"test {} {}"</warning>, 1, Exception()) //warn
          logger?.debug("test {} {}", 1, 2, Exception())
          logger?.debug(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.debug(<warning descr="More arguments provided (2) than placeholders specified (1)">"test {}"</warning>, 1, 2) //warn
          logger?.error(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.info(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.trace(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.warn(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
        }
      """.trimIndent())
  }

  fun `test slf4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory
        import java.util.*

        internal class X {
            var logger = LoggerFactory.getLogger()
            fun m(t: String) {
                logger.info("{} {}", 1, 2)
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning> )
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}" + t + 1</warning> )
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"{}${'$'}t{}"</warning> )
                logger.info("{}${'$'}t{}", 1, 2)
                logger.info("{}${'$'}t{}", 1, 2, 3)
                val temp1 = "{} {}${'$'}t"
                var temp = "{} {}${'$'}t"
                logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">temp1</warning> , 1)
                logger.info(temp, 1, 2, 3)
                logger.info(logText, 1, 2, 3)
                logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">logText</warning> , 1)
                logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">logText2</warning> , 1)
                logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (3)">logText3</warning> , 1)
                temp = "{}${'$'}t"
                logger.info(temp, 1)
            }

            fun m(i: Int, s: String) {
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (1)">"test1 {}"</warning> )
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"test1 {}${'$'}s"</warning> )
                logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (1)">"test1 {}${'$'}i"</warning> )
            }


            companion object {
                private val logText = "{} {}" + something
                private val logText2 = "{} {}" + 1 + "{}" + something
                private const val logText3 = "{} {}" + 1 + "{}"
                private val something: String
                    get() = if (Random().nextBoolean()) "{}" else ""
            }
        }
      """.trimIndent())
  }

  fun `test slf4j builder`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory
        import org.slf4j.spi.LoggingEventBuilder

        fun foo() {
            LoggerFactory.getLogger().atError().log("{}", RuntimeException("test"))
            LoggerFactory.getLogger().atError().log("{} {}", 1, RuntimeException("test"))
            LoggerFactory.getLogger().atError().log( <warning descr="More arguments provided (2) than placeholders specified (1)">"{}"</warning> , 1, RuntimeException("test"))
            logger2.atError().log(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning>, 1)
            
            val loggingEventBuilder = logger2.atError()
            loggingEventBuilder
                .log("{} {}", 2) //skip, because it can be complex cases

            logger2.atError()
            .log(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"{} {}"</warning>, 2) //warn

            logger2.atError()
                .addArgument("s")
                .addKeyValue("1", "1")
                .log("{} {}", 2)
                
            logger2.atError()
            .setMessage(<warning descr="Fewer arguments provided (0) than placeholders specified (2)">"{} {}"</warning>)
            .log()

            logger2.atError()
            .addArgument("")
            .addArgument("")
            .setMessage("{} {}")
            .log()
        }

        private val logger2 = LoggerFactory.getLogger()
        private val builder: LoggingEventBuilder = logger2.atError()
      """.trimIndent())
  }
}