// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.platform.ml.impl.correctness.checker.ErrorsState.Unknown.UnknownReason
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class CodeCorrectnessBuilder {
  private var syntaxState: ErrorsState = ErrorsState.Unknown(UnknownReason.NOT_STARTED)
  private var semanticState: ErrorsState = ErrorsState.Unknown(UnknownReason.NOT_STARTED)
  fun syntaxCorrectness(block: () -> List<CorrectnessError>) {
    syntaxState = ErrorsState.Unknown(UnknownReason.IN_PROGRESS)
    val errors = block()
    syntaxState = selectState(errors)
  }

  fun semanticCorrectness(block: () -> List<CorrectnessError>) {
    semanticState = ErrorsState.Unknown(UnknownReason.IN_PROGRESS)
    val errors = block()
    semanticState = selectState(errors)
  }

  private fun selectState(errors: List<CorrectnessError>): ErrorsState = when {
    errors.isEmpty() -> ErrorsState.Correct
    else -> ErrorsState.Incorrect(errors)
  }

  fun timeLimitExceeded() {
    if ((syntaxState as? ErrorsState.Unknown)?.reason == UnknownReason.IN_PROGRESS) {
      syntaxState = ErrorsState.Unknown(UnknownReason.TIME_LIMIT_EXCEEDED)
    }
    if ((semanticState as? ErrorsState.Unknown)?.reason == UnknownReason.IN_PROGRESS) {
      semanticState = ErrorsState.Unknown(UnknownReason.TIME_LIMIT_EXCEEDED)
    }
  }

  fun build(): CodeCorrectness {
    return CodeCorrectness(syntaxState, semanticState)
  }
}