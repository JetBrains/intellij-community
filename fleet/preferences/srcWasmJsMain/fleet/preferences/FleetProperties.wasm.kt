// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual
import js.string.JsStrings.toKotlinString
import web.window.window
import web.window.Window
import web.url.URL
import js.objects.ReadonlyRecord
import js.string.JsStrings.toKotlinString

private val urlParameters by lazy {
  buildMap {
    URL(window.location.href).searchParams.forEach { value, key ->
      put(key.toKotlinString(), value.toKotlinString())
    }
  }
}

@Actual
fun fleetPropertyWasmJs(name: String, defaultValue: String?): String? {
  val name = name.removePrefix("fleet.")
  return getJsConfigProperty(window, name)
         ?: urlParameters[name.replace('.', '_')]
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
