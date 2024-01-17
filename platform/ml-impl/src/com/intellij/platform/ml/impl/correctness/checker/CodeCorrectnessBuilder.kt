// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.platform.ml.impl.correctness.checker.ErrorsState.Unknown.UnknownReason
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@ApiStatus.Internal
@Deprecated("Use CorrectnessChecker.checkSemantic directly")
@ScheduledForRemoval
class CodeCorrectnessBuilder {
  private var semanticState: ErrorsState = ErrorsState.Unknown(UnknownReason.NOT_STARTED)

  fun semanticCorrectness(block: () -> List<CorrectnessError>) {
    semanticState = ErrorsState.Unknown(UnknownReason.IN_PROGRESS)
    val errors = block()
    semanticState = selectState(errors)
  }

  private fun selectState(errors: List<CorrectnessError>): ErrorsState = when {
    errors.isEmpty() -> ErrorsState.Correct
    else -> ErrorsState.Incorrect(errors)
  }

  fun build(): CodeCorrectness {
    return CodeCorrectness(semanticState)
  }
}