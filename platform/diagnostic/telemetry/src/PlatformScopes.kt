// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic.telemetry


@JvmField
val PLATFORM_METRICS = Scope("platform.metrics")

@JvmField
val EDT = Scope("edt", PLATFORM_METRICS)

@JvmField
val INDEXES = Scope("indexes", PLATFORM_METRICS)

@JvmField
val STORAGE = Scope("storage", PLATFORM_METRICS)

@JvmField
val VFS = Scope("vfs", PLATFORM_METRICS)

@JvmField
val JVM = Scope("jvm", PLATFORM_METRICS)

@JvmField
val COMPLETION_RANKING = Scope("completion.ranking.ml", PLATFORM_METRICS)