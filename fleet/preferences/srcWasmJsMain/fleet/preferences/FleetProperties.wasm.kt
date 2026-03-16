// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import kotlinx.browser.window
import org.w3c.dom.Window

@Actual
fun fleetPropertyWasmJs(name: String, defaultValue: String?): String? {
  return getJsConfigProperty(window, name.removePrefix("fleet."))?.toString() ?: defaultValue
}

private fun getJsConfigProperty(obj: Window, name: String): JsString? {
  js("return (obj['__airConfig'] || {})[name];")
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
