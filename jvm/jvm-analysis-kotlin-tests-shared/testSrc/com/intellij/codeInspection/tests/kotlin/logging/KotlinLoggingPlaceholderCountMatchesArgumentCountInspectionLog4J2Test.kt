package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase(), KotlinPluginModeProvider {
  fun `test log4j2 with text variables`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
          import org.apache.logging.log4j.LogManager


          internal class Logging {
              fun m(i: Int) {
                  val text = "test {}{}{}"
                  LOG.info( <warning descr="Fewer arguments provided (1) than placeholders specified (3)">text</warning> , i)
                  val text2 = "test "
                  LOG.fatal( <warning descr="More arguments provided (1) than placeholders specified (0)">text2</warning> , i)
                  LOG.fatal( <warning descr="Fewer arguments provided (1) than placeholders specified (6)">text + text</warning> , i)
                  LOG.fatal( <warning descr="Fewer arguments provided (1) than placeholders specified (18)">text + text + text + text + text + text</warning> , i)
                  LOG.info( <warning descr="More arguments provided (1) than placeholders specified (0)">FINAL_TEXT</warning> , i)
                  val sum = "first {}" + "second {}" + 1
                  LOG.info( <warning descr="Fewer arguments provided (1) than placeholders specified (2)">sum</warning> , i)
              }

              companion object {
                  private const val FINAL_TEXT = "const"
                  private val LOG = LogManager.getLogger()
              }
          }
          """.trimIndent())
  }

  fun `test log4j2`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
          import org.apache.logging.log4j.*;
          private val logger = LogManager.getLogger();
          private val brackets: String = "{}"
          fun foo() {
              logger?.debug(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.debug(<warning descr="More arguments provided (2) than placeholders specified (1)">"test " + brackets</warning>, 1, 2) //warn
              logger?.debug("test {}" + brackets, 1, 2)
              logger?.debug(<warning descr="Fewer arguments provided (1) than placeholders specified (3)">"test {} {} {}"</warning>, 1, Exception()) //warn
              logger?.debug(<warning descr="More arguments provided (1) than placeholders specified (0)">"test"</warning>, 1, Exception()) //warn
              logger?.debug(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"test {}"</warning>, Exception()) //warn
              logger?.debug("test {} {}", 1, 2, Exception())
              logger?.debug("test {} {}", 1, Exception())
              logger?.debug(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.debug("test {} {}", 1, 2, Exception())
              logger?.debug(<warning descr="More arguments provided (2) than placeholders specified (1)">"test {}"</warning>, 1, 2) //warn
              logger?.error(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.fatal(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.info(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.trace(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.warn(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
              logger?.info("test {} {}", {},  {}, {RuntimeException()})
              logger?.info("test {} {}", {}, {RuntimeException()})
          }
        """.trimIndent())
  }

  fun `test log4j2 builder`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.*;
        private val logger = LogManager.getLogger();
        private val brackets: String = "{}"
        fun foo() {
          logger?.atDebug()?.log(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, 2) //warn
          logger?.atDebug()?.log("test {} {}", 1, 2)
          logger?.atDebug()?.log(<warning descr="More arguments provided (2) than placeholders specified (0)">"test"</warning>, 1, Exception()) //warn
          logger?.atDebug()?.log(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"test {} {} {}"</warning>, 1, Exception()) //warn
          logger?.atDebug()?.log("test {} {} {}", 1, 2, Exception())
          logger?.atDebug()?.log(<warning descr="More arguments provided (3) than placeholders specified (2)">"test {} {}"</warning>, 1, 2, Exception()) //warn
          logger?.atDebug()?.log("test {}", Exception())
          logger?.atDebug()?.log(<warning descr="More arguments provided (1) than placeholders specified (0)">"test"</warning>, Exception()) //warn
        }
        
        internal class Logging {
          fun m(i: Int) {
            LOG.atInfo().log( <warning descr="Fewer arguments provided (1) than placeholders specified (3)">"test? {}{}{}"</warning> , i)
            LOG.atFatal().log( <warning descr="More arguments provided (2) than placeholders specified (0)">"test "</warning> , i, i)
          }
      
          companion object {
              private val LOG = LogManager.getLogger()
          }
      }
        """.trimIndent())
  }

  fun `test log4j2LogBuilder 2`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager
        import org.apache.logging.log4j.util.Supplier
        
        internal class Logging {
            fun m(i: Int) {
              LOG.atInfo().log( <warning descr="Fewer arguments provided (1) than placeholders specified (3)">"test? {}{}{}"</warning> , i)
              LOG.atFatal().log( <warning descr="More arguments provided (2) than placeholders specified (0)">"test "</warning> , i, i)
              LOG.atError().log( <warning descr="More arguments provided (1) than placeholders specified (0)">"test? "</warning> , Supplier<Any> { "" })
            }
        
            companion object {
                private val LOG = LogManager.getLogger()
            }
        }
      """.trimIndent())
  }

  fun `test formatted log4j`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.apache.logging.log4j.*;
      private val LOG: Logger = LogManager.getFormatterLogger()
      fun m() {
          try {
              throw RuntimeException()
          } catch (t: Throwable) {
            val LOG2: Logger = LogManager.getFormatterLogger()
            LOG.info("My %s text", "test", t)
            LOG.info( <warning descr="Illegal format string specifier">"My %i text"</warning> , "test")
            LOG.info( <warning descr="More arguments provided (2) than placeholders specified (1)">"My %s text"</warning> , "test1", "test2")
            LOG2.info("My %s text, %s", "test1") //skip because LOG2 is not final
            LogManager.getFormatterLogger().info( <warning descr="Fewer arguments provided (1) than placeholders specified (2)">"My %s text, %s"</warning> , "test1")
          }
      }
      """.trimIndent())
  }

  fun `test Log4j2 with exception in suppliers`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager
        import org.apache.logging.log4j.util.Supplier

        internal class Logging {
            fun m() {
                try {
                    throw java.lang.RuntimeException()
                } catch (t: IllegalArgumentException) {
                    LOG.info( <warning descr="More arguments provided (3) than placeholders specified (1)">"test {}"</warning> , Supplier<Any> { "test" }, Supplier<Any> { "test" }, Supplier<Any> { t })
                } catch (t: IllegalStateException) {
                    LOG.info(<warning descr="More arguments provided (3) than placeholders specified (1)">"test {}"</warning>, Supplier<Any> { "test" }, Supplier<Any> { "test" }, Supplier<Any> { t })
                } catch (t: Throwable) {
                    LOG.info("test {}", Supplier<Any> { "test" }, Supplier<Any> { t })
                    LOG.info( <warning descr="More arguments provided (3) than placeholders specified (1)">"test {}"</warning> , Supplier<Any> { "test" }, Supplier<Any> { "test" }, Supplier<Any> { t })
                    val s = Supplier { t }
                    LOG.info("test {}", Supplier<Any> { "test" }, s)
                    val s2: Supplier<*> = Supplier<Any> { t }
                    LOG.info("test {}", Supplier<Any> { "test" }, s2)
                    val s3: Supplier<*> = Supplier { t }
                    LOG.info("test {}", Supplier<Any> { "test" }, s3)
                    LOG.info("test {}", Supplier<Any> { "test" }, Supplier<Any> { RuntimeException() })
                    LOG.info("test {}", Supplier<Any> { "test" })
                    LOG.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"test {}"</warning>, {""}, {" "})
                    LOG.info("test {}", {""}, {RuntimeException()})
                    val function: () -> String = { "RuntimeException()" }
                    LOG.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"test {}"</warning>, {""},   function)
                    val function2: () -> RuntimeException = { RuntimeException() }
                    LOG.info("test {}", {""},   function2)
                }
            }

            companion object {
                private val LOG = LogManager.getLogger()
            }
        }
      """.trimIndent())
  }


  fun `test error type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager

        class Log4j {
            fun m() {
              var e = <error descr="[UNRESOLVED_REFERENCE] Unresolved reference: Ce">Ce</error>;
              LOG.error(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"1 {} {} {}"</warning> , <error descr="[DEBUG] Resolved to error element">e</error>, <error descr="[DEBUG] Resolved to error element">e</error>)
            }

            companion object {
                val LOG = LogManager.getLogger()
            }
        }
      """.trimIndent())
  }

  fun `test formatted log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager

        internal object Logging {
            private val logger = LogManager.getFormatterLogger()
            fun test(t: String) {
            logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 1)">"%s" + t + 1 + "%s "</warning> )
            logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"%s %s" + t + 1</warning> )
            logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">"%s %s"</warning>, 1)
            logger.atDebug().log("%s t", 1)
            val logBuilder = logger.atDebug()
            logBuilder.log(<warning descr="More arguments provided (2) than placeholders specified (0)">"%s t"</warning>, 1, 2)
            logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 2)
            logger.info("%s t", 2)
            logger.info(<warning descr="More arguments provided (2) than placeholders specified (1)">"%s t"</warning>, 2, 3) 
            }
        }

      """.trimIndent())
  }

  fun `test log4j with partial known strings`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager
        
        internal object Logging {
            private val logger = LogManager.getLogger()
            fun test(t: String) {
              logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning> )
              logger.info( <warning descr="Fewer arguments provided (0) than placeholders specified (at least 2)">"{}" + t + 1 + "{}"</warning> , RuntimeException())
              logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">"{} {}" + t + 1 + "{}"</warning> , 1, RuntimeException())
              logger.info("{}" + t + 1 + "{}", 1, RuntimeException())
              logger.info("{}" + t + 1 + "{}", 1, 1)
            }
        }
      """.trimIndent())
  }
}