// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed class ErrorsState {
  data object Correct : ErrorsState() {
    override fun errors() = emptyList<CorrectnessError>()
  }

  data class Incorrect(val errors: List<CorrectnessError>) : ErrorsState() {
    init {
      require(errors.isNotEmpty())
    }

    override fun errors() = errors
  }

  data class Unknown(val reason: UnknownReason) : ErrorsState() {
    enum class UnknownReason {
      TIME_LIMIT_EXCEEDED,
      NOT_STARTED,
      IN_PROGRESS
    }

    override fun errors() = emptyList<CorrectnessError>()
  }

  abstract fun errors(): List<CorrectnessError>

  fun List<CorrectnessError>.hasCriticalErrors(): Boolean = any { it.severity == Severity.CRITICAL }
}