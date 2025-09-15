// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
internal fun waitWithParallelismCompensation(runnable: Runnable) {
  @OptIn(InternalCoroutinesApi::class)
  IntellijCoroutines.runAndCompensateParallelism(500.milliseconds, runnable::run)
}