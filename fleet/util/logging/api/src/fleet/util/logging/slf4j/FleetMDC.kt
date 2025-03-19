// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.util.logging.slf4j

import kotlinx.coroutines.slf4j.MDCContext
import kotlinx.coroutines.slf4j.MDCContextMap
import org.slf4j.MDC

object FleetMDC {
  const val WORKSPACE_UID = "workspaceUID"
  const val SHIP_ID = "shipId"
  private const val BACKEND_UID = "backendUID"
  private const val ROLE = "role"
  private const val NETWORK_SENT = "network"

  internal fun role(roleId: String): MDCContext {
    require(roleId.length == 2) { "Rule must be two letters, e.g. \"WS\"" }
    return mdcWith(roleMap(roleId))
  }

  //@fleet.kernel.plugins.InternalInPluginModules(where = ["fleet.testlib"])
  fun forWorkspace(shipId: String, workspaceId: String): MDCContext {
    return mdcWith(mapOf(WORKSPACE_UID to workspaceId, SHIP_ID to shipId) + roleMap("WS"))
  }

  internal fun forBackend(backendId: String): MDCContext {
    return mdcWith(mapOf(BACKEND_UID to backendId) + roleMap("BK"))
  }

  fun forFrontend(shipId: String, workspaceId: String): MDCContext {
    return mdcWith(roleMap("FR") + mapOf(WORKSPACE_UID to workspaceId, SHIP_ID to shipId))
  }

  inline fun <T> withMDC(context: MDCContextMap, f: () -> T): T {
    val previous = MDC.getCopyOfContextMap()
    if (context == null) {
      MDC.clear()
    }
    else {
      MDC.setContextMap(context)
    }
    try {
      return f()
    }
    finally {
      if (previous == null) {
        MDC.clear()
      }
      else {
        MDC.setContextMap(previous)
      }
    }
  }

  private fun roleMap(roleId: String) = mapOf(ROLE to roleId)

  private fun mdcWith(map: Map<String, String>): MDCContext {
    return MDCContext((MDCContext().contextMap ?: emptyMap()) + map)
  }
}