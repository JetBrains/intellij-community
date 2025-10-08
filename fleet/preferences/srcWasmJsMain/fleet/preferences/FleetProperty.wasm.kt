// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import kotlinx.browser.window

@Actual("fleetProperty")
fun fleetPropertyWasmJs(name: String, defaultValue: String?): String? = when (name) {
  "fleet.ai.service.configuration.url" -> url("aiconfig")
  "fleet.ai.service.url" -> url("ai")
  else -> defaultValue
}

@Actual
internal fun getFleetEnvironmentTypeWasmJs(): FleetEnvironmentType {
  return when (ENVIRONMENT) {
    "production" -> FleetEnvironmentType.PRODUCTION
    "development" -> FleetEnvironmentType.DEVELOPMENT
    else -> FleetEnvironmentType.TEST
  }
}

/**
 * the property represents webpack's `config.mode` value
 */
@JsName("ENVIRONMENT")
external val ENVIRONMENT: String

private fun url(suffix: String): String {
  return sequenceOf(window.location.origin, window.location.pathname, suffix)
    .map { it.trim('/') }
    .filter { it.isNotEmpty() }
    .joinToString(separator = "/")
}