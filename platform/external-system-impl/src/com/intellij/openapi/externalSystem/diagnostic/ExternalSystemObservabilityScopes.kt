// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.diagnostic

import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope

@JvmField
val ExternalSystem: Scope = Scope("external.system", PlatformMetrics)

fun forSystem(id: ProjectSystemId): Scope = Scope(id.id.lowercase(), ExternalSystem)
