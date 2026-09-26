// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util

import com.intellij.util.IntelliJCoroutinesFacade
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
fun waitWithParallelismCompensation(runnable: Runnable) {
  IntelliJCoroutinesFacade.runAndCompensateParallelism(500.milliseconds, runnable::run)
}

