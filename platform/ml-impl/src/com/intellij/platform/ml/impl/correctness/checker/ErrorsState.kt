// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@ApiStatus.Internal
@Deprecated("Use CorrectnessChecker.checkSemantic directly")
@ScheduledForRemoval
sealed class ErrorsState {

  data class Analyzed(val errors: List<CorrectnessError>) : ErrorsState() {
    override fun errors() = errors
  }

  data object Unknown : ErrorsState() {
    override fun errors() = emptyList<CorrectnessError>()
  }

  abstract fun errors(): List<CorrectnessError>

  fun List<CorrectnessError>.hasCriticalErrors(): Boolean = any { it.severity == Severity.CRITICAL }
}