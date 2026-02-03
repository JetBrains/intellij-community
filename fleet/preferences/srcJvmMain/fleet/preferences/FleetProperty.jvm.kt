// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.Actual

@Actual
internal fun fleetPropertyJvm(name: String, defaultValue: String?): String? {
  val formattedName = name.replace('.', '_').uppercase()
  return System.getProperty(name) ?: System.getenv(formattedName) ?: defaultValue
}

@Actual
internal fun getFleetEnvironmentTypeJvm(): FleetEnvironmentType {
  return when {
    fleetFlag("fleet.distribution.mode") -> FleetEnvironmentType.PRODUCTION
    fleetFlag("fleet.dev.mode") -> FleetEnvironmentType.DEVELOPMENT
    else -> FleetEnvironmentType.TEST
  }
}