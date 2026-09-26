import org.apache.logging.log4j.LogManager

class StringTemplateAsArgument {
  private val loggerLog4J = LogManager.getLogger()

  fun testException() {
    var exception = RuntimeException()
    val variable1 = 1
    loggerLog4J.info("variable1: $variable1 exception: $exception")
    loggerLog4J.info("exception: $exception")
    loggerLog4J.info("variable1: $variable1", exception)
  }
}