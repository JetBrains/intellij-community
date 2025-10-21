// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import fleet.util.multiplatform.linkToActual

// True means one wants to investigate how fleet itself works (e.g. enable verbose logging or increase transport delays)
val isFleetDebugMode: Boolean by lazy { fleetFlag("fleet.debug.mode") }

// Enable auxiliary features to facilitate dev flow e.g. code reload etc. It means that we're running from sources.
@Deprecated("most probably you want use FleetFromSourcesPaths.isRunningFromSources or FleetCommonSettingsKeys.kt#isInternalMode")
val isFleetDevMode: Boolean by lazy { getFleetEnvironmentType() == FleetEnvironmentType.DEVELOPMENT }

// Defined the default value for internal mode
@Deprecated(
  "we still have usages due to lack of dependency injection, most probably you want use FleetCommonSettingsKeys.kt#isInternalMode")
val isFleetInternalDefaultValue: Boolean by lazy { fleetFlag("fleet.internal.mode.default") }

// Fleet is running as a built distribution
val isFleetDistributionMode: Boolean by lazy { getFleetEnvironmentType() == FleetEnvironmentType.PRODUCTION }

// The distribution is not published on the marketplace, features that depend on it, should do workarounds:
// e.g. upload required resources to the Space in the runtime
val isFleetUnpublishedBuild: Boolean by lazy { fleetFlag("fleet.unpublished.build") }

// Defines whether we are under testing. If set to true, some components will be mocked.
val isFleetTestMode: Boolean by lazy { getFleetEnvironmentType() == FleetEnvironmentType.TEST }

val isFleetShortCircuitMode: Boolean by lazy {
  fleetFlag("fleet.shortCircuit", when {
    isFleetTestMode -> false
    isFleetInternalDefaultValue -> false
    else -> true
  })
}

val coroutinesDebugProbesEnabled: Boolean by lazy { fleetFlag("fleet.coroutines.debug.probes.enabled") }

fun fleetFlag(name: String, defaultValue: Boolean = false): Boolean = fleetProperty(name)?.toBoolean() ?: defaultValue

fun fleetProperty(name: String, defaultValue: String? = null): String? = linkToActual()

internal enum class FleetEnvironmentType {
  PRODUCTION,
  DEVELOPMENT,
  TEST
}

internal fun getFleetEnvironmentType(): FleetEnvironmentType = linkToActual()