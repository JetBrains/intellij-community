// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import io.opentelemetry.sdk.metrics.data.MetricData
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun MetricData.belongsToScope(scope: Scope): Boolean = this.instrumentationScopeInfo.name.startsWith(scope.toString())