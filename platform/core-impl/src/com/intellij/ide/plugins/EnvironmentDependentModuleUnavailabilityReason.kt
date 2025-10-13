// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface EnvironmentDependentModuleUnavailabilityReason

@ApiStatus.Internal
class UnsuitableAppModeModuleUnavailabilityReason(
  val moduleId: PluginModuleId,
  val appMode: @NlsSafe String,
) : EnvironmentDependentModuleUnavailabilityReason