// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.externalSystem.impl.dependencySubstitution

import com.intellij.platform.diagnostic.telemetry.PlatformMetrics
import com.intellij.platform.diagnostic.telemetry.Scope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal val DependencySubstitution: Scope = Scope("dependencySubstitution", PlatformMetrics)