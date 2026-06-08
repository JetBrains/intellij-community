package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.jvm.analysis.testFramework.JvmLanguage

class K2LoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test :
  KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test() {

  fun `test error type`() {
    myFixture.testHighlighting(JvmLanguage.KOTLIN, """
        import org.apache.logging.log4j.LogManager

        class Log4j {
            fun m() {
              var e = <error descr="[UNRESOLVED_REFERENCE] Unresolved reference 'Ce'.">Ce</error>;
              LOG.error(<warning descr="Fewer arguments provided (2) than placeholders specified (3)">"1 {} {} {}"</warning> , e, e)
            }

            companion object {
                val LOG = LogManager.getLogger()
            }
        }
      """.trimIndent())
  }
}