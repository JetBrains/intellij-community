// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package fleet.preferences

import kotlin.random.Random

// True means one wants to investigate how fleet itself works (e.g. enable verbose logging or increase transport delays)
val isFleetDebugMode: Boolean by lazy { fleetFlag("fleet.debug.mode") }

// Enable auxiliary features to facilitate dev flow e.g. code reload etc. It means that we're running from sources.
@Deprecated("most probably you want use FleetFromSourcesPaths.isRunningFromSources or FleetCommonSettingsKeys.kt#isInternalMode")
val isFleetDevMode: Boolean by lazy { fleetFlag("fleet.dev.mode") }

// Defined the default value for internal mode
@Deprecated(
  "we still have usages due to lack of dependency injection, most probably you want use FleetCommonSettingsKeys.kt#isInternalMode")
val isFleetInternalDefaultValue: Boolean by lazy { fleetFlag("fleet.internal.mode.default") }

@Deprecated("remove on next Dock API breakage")
// Key for testing fus internal functionality. Should not be easily accessible from settings.
val isFusInternalMode: Boolean by lazy { fleetFlag("fus.recorder.internal.test.mode") }

// Fleet is running as a built distribution
val isFleetDistributionMode: Boolean by lazy { fleetFlag("fleet.distribution.mode") }

@Deprecated("remove in June 2024")
// Fleet has been built by conveyor thus follows the conveyor distribution layout
val isFleetDistributionConveyorLayout: Boolean by lazy { fleetFlag("fleet.distribution.conveyorLayout") }

// The distribution is not published on the marketplace, features that depend on it, should do workarounds:
// e.g. upload required resources to the Space in the runtime
val isFleetUnpublishedBuild: Boolean by lazy { fleetFlag("fleet.unpublished.build") }

val isUsingMicroSpans: Boolean by lazy { fleetFlag("fleet.track.micro.spans", defaultValue = true) }

// Defines whether we are under testing. If set to true, some components will be mocked.
val isFleetTestMode: Boolean by lazy { !isFleetDevMode && !isFleetDistributionMode }

val isFleetShortCircuitMode: Boolean by lazy {
  fleetFlag("fleet.shortCircuit", when {
    isFleetTestMode -> false
    isFleetInternalDefaultValue -> false
    else -> true
  })
  true
}

val shipAutoUpdateEnabled: Boolean by lazy {
  fleetProperty("fleet.ship.autoUpdate") == "true"
}

val coroutinesDebugProbesEnabled: Boolean by lazy { fleetFlag("fleet.coroutines.debug.probes.enabled") }

fun fleetFlag(name: String, defaultValue: Boolean = false): Boolean = fleetProperty(name)?.toBoolean() ?: defaultValue

fun fleetProperty(name: String, defaultValue: String? = null): String? {
  val formattedName = name.replace('.', '_').uppercase()
  return System.getProperty(name) ?: System.getenv(formattedName) ?: defaultValue
}
