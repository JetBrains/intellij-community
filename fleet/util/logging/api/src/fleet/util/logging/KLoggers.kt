// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import fleet.util.logging.slf4j.Slf4jKLoggerFactory
import java.util.*
import kotlin.reflect.KClass

object KLoggers {
  private val customFactory = ServiceLoader.load(KLoggerFactory::class.java, KLoggerFactory::class.java.classLoader).firstOrNull()
                              ?: Slf4jKLoggerFactory()
  fun logger(owner: KClass<*>): KLogger = customFactory.logger(owner)
  fun logger(owner: Any): KLogger = customFactory.logger(owner)
  fun logger(name: String): KLogger = customFactory.logger(name)
}

inline fun <reified T> logger(): KLogger = KLoggers.logger(T::class)

fun KClass<*>.logger(): KLogger = KLoggers.logger(this)