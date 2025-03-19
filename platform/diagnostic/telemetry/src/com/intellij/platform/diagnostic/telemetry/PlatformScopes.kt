// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry

import org.jetbrains.annotations.ApiStatus.Internal


@Internal
@JvmField
val PlatformMetrics: Scope = Scope("platform.metrics")

@Internal
@JvmField
val EDT: Scope = Scope("edt", PlatformMetrics)

@Internal
@JvmField
val Indexes: Scope = Scope("indexes", PlatformMetrics)

@Internal
@JvmField
val Storage: Scope = Scope("storage", PlatformMetrics)

@Internal
@JvmField
val JPS: Scope = Scope("jps", PlatformMetrics)

@Internal
@JvmField
val Compiler: Scope = Scope("compiler", PlatformMetrics)

@Internal
@JvmField
val WorkspaceModel: Scope = Scope("workspaceModel", PlatformMetrics)

@Internal
@JvmField
val VFS: Scope = Scope("vfs", PlatformMetrics)

@Internal
@JvmField
val JVM: Scope = Scope("jvm", PlatformMetrics)

@Internal
@JvmField
val CompletionRanking: Scope = Scope("completion.ranking.ml", PlatformMetrics)

@Internal
@JvmField
val UI: Scope = Scope("ui", PlatformMetrics)