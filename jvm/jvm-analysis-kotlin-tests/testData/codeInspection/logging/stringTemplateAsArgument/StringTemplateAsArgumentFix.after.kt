import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class StringTemplateAsArgumentFix {
  private val loggerSlf4J = LoggerFactory.getLogger()
  fun testLoggerSlf4J() {
      val variable1 = "test"
      val variable2 = 1
      loggerSlf4J.info("{}", variable1)
      loggerSlf4J.info("{}", variable2)
      loggerSlf4J.info("{}", getMethod())
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.info("variable1: $variable1", RuntimeException())
      loggerSlf4J.info("{} variable1: {}", 1, variable1)
      loggerSlf4J.info("{} variable1: {} {} variable1: {}", 1, variable1, 2, variable1)
      loggerSlf4J.info("{} variable1: {} {} variable1: {} {}", 1, variable1, 2, variable1, 3)
      loggerSlf4J.info("{} variable1: {} {} variable1: {} {}", 1, variable1, 2, variable1, 3, RuntimeException())
      loggerSlf4J.info("{} variable1: {}", 1, variable1, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {} {} {}", 1, variable1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: {} {}", 1, variable1, 2)
  }

  fun getMethod() = 1
}