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

abstract class LoggingContextContextElement(val contextAddition: Map<String, String>) : ThreadContextElement<Map<String, String>?> {
  override fun restoreThreadContext(context: CoroutineContext, oldState: Map<String, String>?) {
    KLoggers.loggerFactory.setLoggingContext(oldState)
  }

  override fun updateThreadContext(context: CoroutineContext): Map<String, String>? {
    val oldState = KLoggers.loggerFactory.getLoggingContext()
    KLoggers.loggerFactory.setLoggingContext((oldState ?: emptyMap()) + contextAddition)
    return oldState
  }
}

class ShipIdContextElement(shipId: String, workspaceId: String) : LoggingContextContextElement(
  mapOf(WORKSPACE_UID_MDC_KEY to workspaceId, SHIP_ID_MDC_KEY to shipId)
) {
  override val key: CoroutineContext.Key<*> get() = ShipIdContextElement

  companion object : CoroutineContext.Key<ShipIdContextElement> {
    private const val WORKSPACE_UID_MDC_KEY: String = "workspaceUID"
    private const val SHIP_ID_MDC_KEY: String = "shipId"

    fun workspaceId(mdcMap: Map<String, String>): String? = mdcMap[WORKSPACE_UID_MDC_KEY]
    fun shipId(mdcMap: Map<String, String>): String? = mdcMap[SHIP_ID_MDC_KEY]
  }
}

class RoleContextElement(role: String) : LoggingContextContextElement(
  mapOf(ROLE_MDC_KEY to role)
) {
  override val key: CoroutineContext.Key<*> get() = RoleContextElement

  companion object : CoroutineContext.Key<RoleContextElement> {
    const val ROLE_MDC_KEY: String = "role"
    fun workspace(): RoleContextElement = RoleContextElement("WS")
    fun frontend(): RoleContextElement = RoleContextElement("FR")

    fun role(mdcMap: Map<String, String>): String? = mdcMap[ROLE_MDC_KEY]
  }
}