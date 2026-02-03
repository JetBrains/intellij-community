import org.slf4j.LoggerFactory

class StringTemplateAsArgumentFix {
  private val loggerSlf4J = LoggerFactory.getLogger()

  val x = 1
  val y = 2

  fun testWithEscape() {
      loggerSlf4J.debug("{}\n{}", x, y)
      loggerSlf4J.debug("{}\t{}", x, y)
      loggerSlf4J.debug("{}\"{}", x, y)
      loggerSlf4J.debug("{}\${}", x, y)
  }
}