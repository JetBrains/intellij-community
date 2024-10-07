// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.utils

import com.intellij.platform.diagnostic.telemetry.IJTracer
import com.intellij.platform.diagnostic.telemetry.Scope
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
val SEMANTIC_SEARCH_TRACER: IJTracer = TelemetryManager.getInstance().getTracer(Scope("semanticSearch"))