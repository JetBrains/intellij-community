package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.codeInspection.logging.LoggingPlaceholderCountMatchesArgumentCountInspection
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase
import com.intellij.jvm.analysis.testFramework.JvmLanguage
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

abstract class KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionPlaceholderNumberTest : LoggingPlaceholderCountMatchesArgumentCountInspectionTestBase(), KotlinPluginModeProvider {
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

  fun `test lazy init`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
      import org.apache.logging.log4j.LogBuilder
      import org.apache.logging.log4j.LogManager
      import org.apache.logging.log4j.Logger

      class LazyInitializer {

          internal class StaticInitializerBuilder2 {
              init {
                  log.log("{}")
              }

              companion object {
                  private val log: LogBuilder

                  init {
                      if (1 == 1) {
                          log   = LogManager.getLogger().atDebug()
                      } else {
                          log = LogManager.getFormatterLogger().atDebug()
                      }
                  }
              }
          }

          internal class ConstructorInitializer {
              private val log: Logger

              constructor() {
                  log = LogManager.getLogger()
              }

              constructor(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used">i</warning>: Int) {
                  log = LogManager.getLogger()
              }

              fun test() {
                log.info(<warning descr="Fewer arguments provided (0) than placeholders specified (1)">"{}"</warning>)
              }
          }

          internal class ConstructorInitializer2 {
              private val log: Logger

              constructor() {
                  log = LogManager.getFormatterLogger()
              }

              constructor(<warning descr="[UNUSED_PARAMETER] Parameter 'i' is never used">i</warning>: Int) {
                  log = LogManager.getLogger()
              }

              fun test() {
                log.info("{}")
              }
          }
      }
      """.trimIndent())
  }
}