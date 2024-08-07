package com.intellij.customization.console

import com.intellij.jvm.analysis.internal.testFramework.internal.LogFinderHyperlinkTestBase
import com.intellij.jvm.analysis.internal.testFramework.internal.LogItem
import com.intellij.jvm.analysis.internal.testFramework.logging.LoggingTestUtils
import com.intellij.openapi.editor.LogicalPosition
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

@Suppress("ConvertToStringTemplate")
abstract class KotlinLogFinderHyperlinkTest : LogFinderHyperlinkTestBase(), KotlinPluginModeProvider {
  fun testSimpleLog4j2() {
    LoggingTestUtils.addLog4J(myFixture)
    checkColumnFinderKotlin(
      fileName = "Log4jk",
      classText = """
package com.example.loggingjava.java

import org.apache.logging.log4j.LogManager

val logOutside = LogManager.getLogger()

fun log() {
    logOutside.info("top level logOutside")
    Log4jk.log.info("top level logCompanion")
}

class Log4jk {
    fun log() {
        logOutside.info("inside method logOutside")
        logInside.info("inside method logInside")
        log.info("inside method logCompanion")
    }

    private val logInside = LogManager.getLogger()

    companion object {
        val log = LogManager.getLogger()


        fun log() {
            logOutside.info("inside companion logOutside")
            log.info("inside companion logCompanion")
        }
    }
}

fun main() {
    Log4jk().log()
    Log4jk.log()
    log()
}
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe AppMainV2 com.example.l.java.Log4jkKt", LogicalPosition(0, 0)),
        LogItem("09:52:49.884 [main] INFO com.example.logg.java.Log4jkKt - inside method logOutside", LogicalPosition(13, 19)),
        LogItem("09:52:49.888 [main] INFO com.e.log.java.Log4jk - inside method logInside", LogicalPosition(14, 18)),
        LogItem("09:52:49.888 [main] INFO com.example.loggin.java.Log4jk - inside method logCompanion", LogicalPosition(15, 12)),
        LogItem("09:52:49.888 [main] INFO com.example.logg.java.Log4jkKt - inside companion logOutside", LogicalPosition(25, 23)),
        LogItem("09:52:49.888 [main] INFO com.example.log.j.Log4jk - inside companion logCompanion", LogicalPosition(26, 16)),
        LogItem("09:52:49.888 [main] INFO com.e.loggingjava.java.Log4jkKt - top level logOutside", LogicalPosition(7, 15)),
        LogItem("09:52:49.888 [main] INFO c.example.loggingjava.java.Log4jk - top level logCompanion", LogicalPosition(8, 15)),
      )
    )
  }

  fun testTopLevelSlf4j() {
    LoggingTestUtils.addSlf4J(myFixture)
    checkColumnFinderKotlin(
      fileName = "Slf4Jk",
      classText = """
package com.example.loggingjava.java

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger(Slf4Jk::class.java)

private fun log1(i: Int) {
    val msg = getMsg(i)
    log.info(msg)
}

private fun log2(i: Int) {
    val msg = "log2" + i
    log.info(msg)
}

private fun getMsg(i: Int): String {
    return "test" + i
}

fun main(args: Array<String>) {
    println(2)
    log1(1)
    log2(2)
}

object Slf4Jk 
""".trimIndent(),
      logItems = listOf(
        LogItem("java.exe AppMainV2 com.example.l.java.Slf4JkKt", LogicalPosition(0, 0)),
        LogItem("10:34:44.491 [main] INFO com.example.l.j.Slf4Jk - test1", LogicalPosition(27, 7)),
        LogItem("10:34:44.495 [main] INFO com.example.l.j.Slf4Jk - log22", LogicalPosition(14, 8)),
      )
    )
  }
}