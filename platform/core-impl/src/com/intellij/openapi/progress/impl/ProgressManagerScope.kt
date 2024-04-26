// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.impl

import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@JvmField
val ProgressManagerScope: Scope = Scope("progressManager", PlatformMetrics)
