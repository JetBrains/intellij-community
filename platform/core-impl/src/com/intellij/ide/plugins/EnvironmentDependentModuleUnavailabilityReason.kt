// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface EnvironmentDependentModuleUnavailabilityReason {
  val logMessage: String
}

@ApiStatus.Internal
class UnsuitableProductModeModuleUnavailabilityReason(
  val moduleId: PluginModuleId,
  val productMode: @NlsSafe String,
) : EnvironmentDependentModuleUnavailabilityReason {
  override val logMessage: String get() = "Module '${moduleId.name}' is not available in '$productMode' product mode"
}