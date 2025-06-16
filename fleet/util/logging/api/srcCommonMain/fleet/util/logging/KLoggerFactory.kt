// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging

import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

interface KLoggerFactory {
  fun logger(owner: KClass<*>): KLogger
  fun logger(owner: Any): KLogger
  fun logger(name: String): KLogger

  fun setLoggingContext(map: Map<String, String>?)
  fun getLoggingContext(): Map<String, String>?
}

//todo [MM] make it abstract with next dock breaking change
open class LoggingContextContextElement(val contextMap/*todo [MM] rename to AdditionMap*/: Map<String, String>) : ThreadContextElement<Map<String, String>?> {
  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
    KLoggers.loggerFactory.setLoggingContext(oldState)
  }

  override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
    val oldState = KLoggers.loggerFactory.getLoggingContext()
    KLoggers.loggerFactory.setLoggingContext((oldState ?: emptyMap()) + contextMap)
    return oldState
  }

  @Deprecated("Use ShipIdLoggingContextElement/RoleLoggingContextElement", level = DeprecationLevel.WARNING)
  override val key: CoroutineContext.Key<*> get() = LoggingContextContextElement

  @Deprecated("Use ShipIdLoggingContextElement/RoleLoggingContextElement", level = DeprecationLevel.WARNING)
  companion object : CoroutineContext.Key<LoggingContextContextElement>
}

