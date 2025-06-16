// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.withContext
import kotlin.collections.plus
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.reflect.KClass

interface KLoggerFactory {
  fun logger(owner: KClass<*>): KLogger
  fun logger(owner: Any): KLogger
  fun logger(name: String): KLogger

  fun setLoggingContext(map: Map<String, String>?)
  fun getLoggingContext(): Map<String, String>?
}

suspend fun <T> withAdditionalLoggingContext(addition: Map<String, String>, body: suspend CoroutineScope.() -> T): T {
  val currentContext = coroutineContext[LoggingContextContextElement]?.contextMap ?: emptyMap()
  val newContext = LoggingContextContextElement(currentContext + addition)
  return withContext(newContext, body)
}

class LoggingContextContextElement(val contextMap: Map<String, String>?) : ThreadContextElement<Map<String, String>?> {
  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
    KLoggers.loggerFactory.setLoggingContext(oldState)
  }

  override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
    val oldState = KLoggers.loggerFactory.getLoggingContext()
    KLoggers.loggerFactory.setLoggingContext(contextMap)
    return oldState
  }

  override val key: CoroutineContext.Key<*> get() = LoggingContextContextElement

  companion object : CoroutineContext.Key<LoggingContextContextElement>
}

