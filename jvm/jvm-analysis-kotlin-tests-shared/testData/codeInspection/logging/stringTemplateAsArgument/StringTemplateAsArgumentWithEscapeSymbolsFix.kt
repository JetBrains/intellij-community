import org.slf4j.LoggerFactory

class StringTemplateAsArgumentFix {
  private val loggerSlf4J = LoggerFactory.getLogger()

  val x = 1
  val y = 2

  fun testWithEscape() {
    loggerSlf4J.de<caret>bug("$x\n$y")
    loggerSlf4J.debug("$x\t$y")
    loggerSlf4J.debug("$x\"$y")
    loggerSlf4J.debug("$x$$y")
  }
}