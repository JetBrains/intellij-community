import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

data class Data(val i: Int)

class StringTemplateAsArgumentSkipPrimitives {
  private val loggerSlf4J = LoggerFactory.getLogger()
  private val loggerLog4J = LogManager.getLogger()

  fun testLoggerSlf4JBuilder() {
    val variable1 = 1
    val data = Data(1)
    loggerSlf4J.atInfo().log("variable1: ${variable1}")
    loggerSlf4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("data: ${data}")
  }

  fun testLoggerLog4J() {
    val variable1 = 1
    val data = Data(1)
    loggerLog4J.info("variable1: ${variable1}")
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("data: ${data}")
  }

  fun testLoggerLog4JBuilder() {
    val variable1 = 1
    val data = Data(1)
    loggerLog4J.atInfo().log( "variable1: ${variable1}")
    loggerLog4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>( "data: ${data}")
  }

  fun testLoggerSlf4J() {
    val variable1 = 1
    val data = Data(1)
    loggerSlf4J.info("variable1: {}", variable1)
    loggerSlf4J.info("${variable1}")
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("${data}")
  }
}