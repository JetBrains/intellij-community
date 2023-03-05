// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry

val connectionMetricsPath: String = System.getProperty("idea.diagnostic.opentelemetry.metrics.file",
                                                       "open-telemetry-connection-metrics.gz")