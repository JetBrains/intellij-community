// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.logging.slf4j.Slf4jKLoggerFactory
import java.util.ServiceLoader
import kotlin.reflect.KClass

object KLoggers {
  private val customFactory = ServiceLoader.load(KLoggerFactory::class.java, KLoggerFactory::class.java.classLoader).firstOrNull()
                              ?: Slf4jKLoggerFactory()
  fun logger(owner: KClass<*>) = customFactory.logger(owner)
  fun logger(owner: Class<*>) = customFactory.logger(owner)
  fun logger(owner: Any) = customFactory.logger(owner)
  fun logger(name: String) = customFactory.logger(name)

  // Creates logger named according to file FQN
  // inlined internal lambda will capture call-site scope
  @Suppress("NOTHING_TO_INLINE")
  inline fun logger(): KLogger {
    val nameSource = { }
    return logger(loggerNameFromSource(nameSource))
  }
}

inline fun <reified T> logger() = KLoggers.logger(T::class)

fun KClass<*>.logger() = KLoggers.logger(this)

abstract class KLogging(target: KClass<*>? = null) {
  val logger: KLogger by lazy { KLoggers.logger(target ?: this::class) }
}

internal typealias LoggerNameSource = () -> Unit

fun loggerNameFromSource(nameSource: LoggerNameSource): String {
  val name = nameSource.javaClass.name
  return when {
    name.contains("Kt$") -> name.substringBefore("Kt$")
    name.contains("$") -> name.substringBefore("$")
    else -> name
  }
}