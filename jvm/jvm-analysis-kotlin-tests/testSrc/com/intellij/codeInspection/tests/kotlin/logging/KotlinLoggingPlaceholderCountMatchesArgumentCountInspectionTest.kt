package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
class KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionTest {

  class PlaceholderNumberTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase() {

    fun `test many variables`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory

        internal class X {
            var LOGGER = LoggerFactory.getLogger()
            fun m() {
                val a1 = " {} " +" s"
                val a2 = a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1 + a1
                LOGGER.info( <warning descr="Fewer arguments provided (1) than placeholders specified (at least 3)">"abd"+a2</warning> , 1)
            }
        }
      """.trimIndent())
    }
    fun `test variables`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory
        private const val con = "{}"

        internal class X {
            var logger = LoggerFactory.getLogger()
            private var con2 = "{}"
                
            private fun slf4jVariable3(number: String) {
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
              logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
              var t = "{}"
              logger.info(con2 + t + "1", 1)
              logger.info(con2 + t + "1", 1)
            }
        
            private fun slf4jVariable(number: String) {
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
                var t = "{}"
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">con + t + "1"</warning>, 1) //warn
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (2)">con + t + "1"</warning>, 1) //warn
            }
        
            private fun slf4jVariable2(number: String) {
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
                logger.info(<warning descr="Fewer arguments provided (1) than placeholders specified (at least 2)">number + "1 {} {}"</warning>, 1) //warn
                var t = "{}"
                logger.info(con + t + "1", 1)
                t = "1"
                logger.info(con + t + "1", 1)
            }

          }
      """.trimIndent())
    }
    fun `test fewer and more placeholders`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.*;
        internal class X {
            fun foo() {
                val logger = LoggerFactory.getLogger()
                logger.info( <warning descr="Fewer arguments provided (1) than placeholders specified (2)">"string {}{}"</warning> , 1)
                logger.info( <warning descr="More arguments provided (1) than placeholders specified (0)">"string"</warning> , 1)
            }
        }
      """.trimIndent())
    }

    fun `test escaping`() {
      inspection.slf4jToLog4J2Type = LoggingPlaceholderCountMatchesArgumentCountInspection.Slf4jToLog4J2Type.NO
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory


        internal class X {
            var LOG = LoggerFactory.getLogger()
            fun m() {
                LOG.info("Created key {}\\\\{}", 1, 2)
                LOG.info( <warning descr="More arguments provided (2) than placeholders specified (1)">"Created key {}\\{}"</warning> , 1, 2)
            }
        }
        """.trimIndent())
    }

    fun `test non constant string`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory

        internal class X {
            val LOG = LoggerFactory.getLogger()
            fun m() {
              LOG.info( <warning descr="Fewer arguments provided (0) than placeholders specified (4)">S + "{}" + (1 + 2) + '{' + '}' + S</warning> )
              LOG.info( <warning descr="Fewer arguments provided (0) than placeholders specified (1)">message</warning> )
            }

            companion object {
                private const val message = "HELLO {}"
                private const val S = "{}"
            }
        }
        """.trimIndent())
    }

    fun `test 1 exception and several placeholder`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.slf4j.LoggerFactory

        internal class X {
            fun foo() {
                val e = RuntimeException()
                LoggerFactory.getLogger().info( <warning descr="Fewer arguments provided (0) than placeholders specified (1)">"this: {}"</warning> , e)
                LoggerFactory.getLogger().info("1: {} e: {}", 1, e)
                LoggerFactory.getLogger().info( <warning descr="Fewer arguments provided (1) than placeholders specified (3)">"1: {} {} {}"</warning> , 1, e)
                val logger = LoggerFactory.getLogger()
                logger.info("string {}", 1)
            }
        }
      """.trimIndent())
    }

    fun `test throwable`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.LoggerFactory

      internal class X {
          fun foo() {
              val logger = LoggerFactory.getLogger()
              logger.info("string {}", 1, RuntimeException())
          }
      }
        """.trimIndent())
    }

    fun `test multi catch`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.LoggerFactory

      internal class X {
          fun multiCatch() {
              try {
                  method()
              } catch (e: FirstException) {
                  logger.info("failed with first or second", e)
              } catch (e: SecondException) {
                  logger.info("failed with first or second", e)
              }
          }

          fun method() {
          }

          class FirstException : Exception()
          class SecondException : Exception()
          companion object {
              private val logger = LoggerFactory.getLogger()
          }
      }
        """.trimIndent())
    }

    fun `test no slf4j`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      internal class FalsePositiveSLF4J {
          fun method(definitelyNotSLF4J: DefinitelyNotSLF4J) {
              definitelyNotSLF4J.info("not a trace message", "not a trace parameter")
          }

          interface DefinitelyNotSLF4J {
              fun info(firstParameter: String?, secondParameter: Any?)
          }
      }
         """.trimIndent())
    }

    fun `test array argument`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.LoggerFactory

      internal class X {
          var LOG = LoggerFactory.getLogger()
          fun m(a: String, b: Int, c: Any) {
              LOG.info("test {} for test {} in test {}", *arrayOf(a, b, c))
          }
      }
         """.trimIndent())
    }

    fun `test uncountable array`() {
      myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.slf4j.LoggerFactory

      internal class X {
          var LOG = LoggerFactory.getLogger()
          fun m(objects: Array<Any?>) {
              LOG.info("test {} test text {} text {}", *objects)
          }
      }
         """.trimIndent())
    }
  }

  class Log4J2Test : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase() {

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


    fun `test error type`(){
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

  class Slf4JTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase() {
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
}