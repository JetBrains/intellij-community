// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual

@Actual
fun fleetPropertyNative(name: String, defaultValue: String?): String? = defaultValue

@Actual
internal fun getFleetEnvironmentTypeNative(): FleetEnvironmentType {
  return when (ENVIRONMENT) {
    "production" -> FleetEnvironmentType.PRODUCTION
    "development" -> FleetEnvironmentType.DEVELOPMENT
    else -> FleetEnvironmentType.TEST
  }
}

/**
 * the property represents webpack's `config.mode` value
 */
val ENVIRONMENT: String = "development" // TODO
