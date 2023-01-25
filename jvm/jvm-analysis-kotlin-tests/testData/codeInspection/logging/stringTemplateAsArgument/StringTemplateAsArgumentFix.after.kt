import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

class StringTemplateAsArgumentFix {
  private val loggerSlf4J = LoggerFactory.getLogger()
  fun testLoggerSlf4J() {
      val variable1 = "test"
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: {}", variable1, RuntimeException())
      loggerSlf4J.info("{} variable1: {}", 1, variable1, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {} {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {}", 1, variable1, 2)
  }
}