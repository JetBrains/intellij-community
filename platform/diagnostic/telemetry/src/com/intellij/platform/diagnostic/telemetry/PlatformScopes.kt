// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry


@JvmField
val PlatformMetrics: Scope = Scope("platform.metrics")

@JvmField
val EDT: Scope = Scope("edt", PlatformMetrics)

@JvmField
val Indexes: Scope = Scope("indexes", PlatformMetrics)

@JvmField
val Storage: Scope = Scope("storage", PlatformMetrics)

@JvmField
val JPS: Scope = Scope("jps", PlatformMetrics)

@JvmField
val WorkspaceModel: Scope = Scope("workspaceModel", PlatformMetrics)

@JvmField
val VFS: Scope = Scope("vfs", PlatformMetrics)

@JvmField
val JVM: Scope = Scope("jvm", PlatformMetrics)

@JvmField
val CompletionRanking: Scope = Scope("completion.ranking.ml", PlatformMetrics)