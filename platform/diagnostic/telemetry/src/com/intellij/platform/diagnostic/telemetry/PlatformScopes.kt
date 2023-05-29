// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.diagnostic.telemetry


@JvmField
val PlatformMetrics = Scope("platform.metrics")

@JvmField
val EDT = Scope("edt", PlatformMetrics)

@JvmField
val Indexes = Scope("indexes", PlatformMetrics)

@JvmField
val Storage = Scope("storage", PlatformMetrics)

@JvmField
val JPS = Scope("jps", PlatformMetrics)

@JvmField
val VFS = Scope("vfs", PlatformMetrics)

@JvmField
val JVM = Scope("jvm", PlatformMetrics)

@JvmField
val CompletionRanking = Scope("completion.ranking.ml", PlatformMetrics)