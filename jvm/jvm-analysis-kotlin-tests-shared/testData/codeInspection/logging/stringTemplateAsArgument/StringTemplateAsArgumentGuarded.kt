import org.apache.logging.log4j.LogManager
import org.slf4j.LoggerFactory

data class Data(val i: Int)
class StringTemplateAsArgumentGuarded {
  private val loggerSlf4J = LoggerFactory.getLogger()
  private val loggerLog4J = LogManager.getLogger()


  fun guardedLog4J() {
    val data = Data(1)
    if (loggerLog4J.isInfoEnabled) {
      loggerLog4J.info("$data" )
    }
    if (loggerLog4J.isInfoEnabled()) {
      loggerLog4J.info("$data" )
    }
    loggerLog4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("$data")
  }


  fun guardedLog4JBuilder() {
    val data = Data(1)
    val atInfo = loggerLog4J.atInfo()
    if (loggerLog4J.isInfoEnabled) {
      loggerLog4J.atInfo().log("$data"  )
    }
    if (loggerLog4J.isInfoEnabled) {
      atInfo.log("$data"   )
    }
    if (loggerLog4J.isDebugEnabled) {
      atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data"   )
    }
    if (loggerLog4J.isDebugEnabled()) {
      atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data"   )
    }
    loggerLog4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("$data" )
    atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data" )
  }

  fun guardedSlf4j() {
    val data = Data(1)
    if (loggerSlf4J.isInfoEnabled) {
      loggerSlf4J.info("$data" )
    }
    if (loggerSlf4J.isInfoEnabled()) {
      loggerSlf4J.info("$data" )
    }
    loggerSlf4J.<warning descr="String template as argument to 'info()' logging call">info</warning>("$data")
  }

  fun guardedSlf4jBuilder() {
    val data = Data(1)
    val atInfo = loggerSlf4J.atInfo()
    if (loggerSlf4J.isInfoEnabled) {
      loggerSlf4J.atInfo().log("$data"  )
    }
    if (loggerSlf4J.isInfoEnabled) {
      atInfo.log("$data"   )
    }
    if (loggerSlf4J.isDebugEnabled) {
      atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data"   )
    }
    if (loggerSlf4J.isDebugEnabled()) {
      atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data"   )
    }
    loggerSlf4J.atInfo().<warning descr="String template as argument to 'log()' logging call">log</warning>("$data" )
    atInfo.<warning descr="String template as argument to 'log()' logging call">log</warning>("$data" )
  }
}