import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

class StringTemplateAsArgument {
  private val loggerSlf4J = LoggerFactory.getLogger()
  private val loggerLog4J = LogManager.getLogger()

  fun testLoggerSlf4JBuilder() {
    val variable1 = 1
    loggerSlf4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("variable1: ${variable1}")
  }

  fun testLoggerLog4J() {
    val variable1 = 1
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
  }

  fun testLoggerLog4JBuilder() {
    val variable1 = 1
    loggerLog4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>( "variable1: ${variable1}")
  }


  fun testException(){
    var exception = RuntimeException()
    val variable1 = 1
    loggerLog4J.info("variable1: $variable1 exception: $exception")
    loggerLog4J.info("exception: $exception")
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: $variable1", exception)
  }

  fun testLoggerSlf4J() {
    val variable1 = 1
    loggerSlf4J.info("variable1: {}", variable1)
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("${variable1}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("${getString()}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("${getInt()}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: ${variable1}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: $variable1")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("variable1: $variable1", RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1", 1, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {} {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {} {} {}", 1, 2, RuntimeException())
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("{} variable1: $variable1 {}", 1, 2)
  }

  fun getString() = "test"
  fun getInt() = 1
}