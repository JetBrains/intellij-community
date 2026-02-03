// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.dashboard

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

// this thingy exists solely because we disallow using a serialization library in api modules - we have to reconsider to avoid polluting code with useless abstractions
@ApiStatus.Internal
interface LegacyRunDashboardServiceSubstitutor {
  companion object {
    @JvmField
    val EP_NAME = ExtensionPointName<LegacyRunDashboardServiceSubstitutor>("com.intellij.legacyRunDashboardServiceSubstitutor")
  }

  // Returns backend counterpart of a given service or self
  fun substituteWithBackendService(maybeFrontendConfigurationNode: RunDashboardRunConfigurationNode, project: Project): RunDashboardRunConfigurationNode
}