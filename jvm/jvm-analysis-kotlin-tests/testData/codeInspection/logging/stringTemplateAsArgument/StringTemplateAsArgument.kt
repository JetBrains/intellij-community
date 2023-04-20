import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

class StringTemplateAsArgument {
  private val loggerSlf4J = LoggerFactory.getLogger()
  private val loggerLog4J = LogManager.getLogger()

  fun testLoggerSlf4JBuilder() {
    val variable1 = "test"
    loggerSlf4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("variable1: ${variable1}")
  }

  fun testLoggerLog4J() {
    val variable1 = "test"
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
  }

  fun testLoggerLog4JBuilder() {
    val variable1 = "test"
    loggerLog4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>( "variable1: ${variable1}")
  }

  fun testLoggerSlf4J() {
    val variable1 = "test"
    loggerSlf4J.info("variable1: {}", variable1)
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: $variable1")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: $variable1", RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1", 1, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {} {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {} {} {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {}", 1, 2)
  }
}