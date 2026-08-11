// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInsight.util

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import org.jetbrains.annotations.ApiStatus

@JvmField
val InspectionScope: Scope = Scope("inspection")

@JvmField
val InspectionTracer: IJTracer = TelemetryManager.getTracer(InspectionScope)
