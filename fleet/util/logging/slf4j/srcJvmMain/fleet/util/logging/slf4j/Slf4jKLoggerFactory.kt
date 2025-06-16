// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging.slf4j

import fleet.util.logging.KLogger
import fleet.util.logging.KLoggerFactory
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import kotlin.reflect.KClass

class Slf4jKLoggerFactory : KLoggerFactory {
  override fun logger(owner: KClass<*>): KLogger {
    return defaultLogger(owner)
  }

  override fun logger(owner: Any): KLogger {
    return defaultLogger(owner.javaClass.kotlin)
  }

  override fun logger(name: String): KLogger {
    return defaultLogger(name)
  }

  private fun defaultLogger(name: String): KLogger {
    val slf4jLogger = LoggerFactory.getLogger(name)
    return KLogger(JVMLogger(slf4jLogger))
  }

  private fun defaultLogger(clazz: KClass<*>): KLogger {
    return defaultLogger(clazz.java)
  }

  private fun defaultLogger(clazz: Class<*>): KLogger {
    val slf4jLogger = LoggerFactory.getLogger(clazz)
    return KLogger(JVMLogger(slf4jLogger))
  }

  override fun getLoggingContext(): Map<String, String>? {
    return MDC.getCopyOfContextMap()
  }

  override fun setLoggingContext(map: Map<String, String>?) {
    if (map == null) {
      MDC.clear()
    }
    else {
      MDC.setContextMap(map)
    }
  }
}