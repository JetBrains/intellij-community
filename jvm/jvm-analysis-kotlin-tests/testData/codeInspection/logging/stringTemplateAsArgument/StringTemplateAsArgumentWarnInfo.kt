import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

class StringTemplateAsArgumentWarnInfo {
  private val loggerSlf4J = LoggerFactory.getLogger()
  private val loggerLog4J = LogManager.getLogger()

  fun testLoggerSlf4JBuilder() {
    val variable1 = 1
    loggerSlf4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("variable1: ${variable1}")
    loggerSlf4J.atDebug().<warning descr="String template as argument to 'log()' logging call">log</warning>("variable1: ${variable1}")
    loggerSlf4J.atWarn().log("variable1: ${variable1}")
  }

  fun testLoggerLog4J() {
    val variable1 = 1
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
    loggerLog4J.<warning descr="String template as argument to 'debug()' logging call">debug</warning>("variable1: ${variable1}")
    loggerLog4J.warn("variable1: ${variable1}")
  }

  fun testLoggerLog4JBuilder() {
    val variable1 = 1
    loggerLog4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>( "variable1: ${variable1}")
    loggerLog4J.atDebug().<warning descr="String template as argument to 'log()' logging call">log</warning>( "variable1: ${variable1}")
    loggerLog4J.atWarn().log( "variable1: ${variable1}")
  }

  fun testLoggerSlf4J() {
    val variable1 = 1
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
    loggerSlf4J.<warning descr="String template as argument to 'debug()' logging call">debug</warning>("variable1: ${variable1}")
    loggerSlf4J.warn("variable1: ${variable1}")
  }

  fun getString() = "test"
  fun getInt() = 1
}