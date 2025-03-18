// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import java.util.*
import kotlin.reflect.KClass

object KLoggers {
  internal val loggerFactory: KLoggerFactory = ServiceLoader.load(KLoggerFactory::class.java, KLoggerFactory::class.java.classLoader).first()

  fun logger(owner: KClass<*>): KLogger = loggerFactory.logger(owner)
  fun logger(owner: Any): KLogger = loggerFactory.logger(owner)
  fun logger(name: String): KLogger = loggerFactory.logger(name)

  fun setLoggingContext(map: Map<String, String>?): Unit = loggerFactory.setLoggingContext(map)
  fun getLoggingContext(): Map<String, String>? = loggerFactory.getLoggingContext()
}

inline fun <reified T> logger(): KLogger = KLoggers.logger(T::class)

fun KClass<*>.logger(): KLogger = KLoggers.logger(this)