// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import js.string.JsStrings.toKotlinString
import web.window.window
import web.window.Window
import web.url.URL
import js.objects.ReadonlyRecord
import js.string.JsStrings.toKotlinString

private val allowedPropertiesPassedInQuery = setOf(
  "fahrplan.startup",
  "fleet.connect.host",
  "fleet.connect.identity",
  "fleet.connect.join.url",
  "fleet.connect.port",
  "fleet.connect.secret",
  "fleet.connect.secure",
  "fleet.diagnostic.fahrplan.enabled",
  "fleet.dock.api.only",
  "fleet.jcp",
  "fleet.ship.type",
  "fleet.startup.performance.checkpoints",
  "fleet.transient.agent-session-id",
)

private val urlParameters by lazy {
  buildMap {
    URL(window.location.href).searchParams.forEach { value, key ->
      put(key.toKotlinString(), value.toKotlinString())
    }
  }
}

@Actual
fun fleetPropertyWasmJs(name: String, defaultValue: String?): String? {
  val nameWithoutPrefix = name.removePrefix("fleet.")
  return getJsConfigProperty(window, nameWithoutPrefix)
         ?: urlParameters[nameWithoutPrefix.replace('.', '_')].takeIf { name in allowedPropertiesPassedInQuery }
         ?: defaultValue
}

private fun getJsConfigProperty(obj: Window, name: String): String? {
  return window["__airConfig"]?.unsafeCast<ReadonlyRecord<JsString, JsString>>()?.get(name.toJsString())?.toKotlinString()
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
