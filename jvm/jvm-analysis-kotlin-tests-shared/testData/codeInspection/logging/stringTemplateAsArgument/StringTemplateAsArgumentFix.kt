import org.slf4j.LoggerFactory
import java.lang.RuntimeException

class StringTemplateAsArgumentFix {
  private val loggerSlf4J = LoggerFactory.getLogger()

  fun testLoggerSlf4J() {
      val variable1 = "test"
      val variable2 = 1
      loggerSlf4J.info("${variable1}")
      loggerSlf4J.info("${variable2}")
      loggerSlf4J.info("${getMethod()}")
      loggerSlf4J.info("variable1: {}", variable1)
      loggerSlf4J.in<caret>fo("variable1: ${variable1}")
      loggerSlf4J.info("variable1: $variable1")
      loggerSlf4J.info("variable1: $variable1", RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1", 1)
      loggerSlf4J.info("{} variable1: $variable1 {} variable1: $variable1", 1, 2)
      loggerSlf4J.info("{} variable1: $variable1 {} variable1: $variable1 {}", 1, 2, 3)
      loggerSlf4J.info("{} variable1: $variable1 {} variable1: $variable1 {}", 1, 2, 3, RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1", 1, RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1 {}", 1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1 {} {}", 1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1 {} {} {}", 1, 2, RuntimeException())
      loggerSlf4J.info("{} variable1: $variable1 {}", 1, 2)
      loggerSlf4J.info("{} variable1: $variable1 {}" + "{} variable1: $variable1 {}", 1, 2, 3, 4)
  }

  fun getMethod() = 1
}