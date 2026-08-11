// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@JvmField
val jsonSchemaScope = Scope("jsonSchema")

@get:ApiStatus.Internal
val jsonSchemaTracer: IJTracer get() = TelemetryManager.getTracer(jsonSchemaScope)
