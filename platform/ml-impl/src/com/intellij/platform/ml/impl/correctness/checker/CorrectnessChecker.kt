// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus

/**
 * Finds [CorrectnessError]s in the completion suggestions.
 */
@ApiStatus.Internal
interface CorrectnessChecker {
  data class CheckResult(val errors: List<CorrectnessError>, val fileWithSuggestion: PsiFile, val suggestionRange: TextRange)

  /**
   * Finds [CorrectnessError]s in the completion suggestions provided by a Full Line model.
   * @param file original file
   * @param suggestion the suggested by a model completion text
   * @param offset the caret offset in [file]
   * @param prefix a part of the [suggestion] that is already present in the [file] before [offset]
   */
  @RequiresBlockingContext
  fun checkSemantic(file: PsiFile,
                    suggestion: String,
                    offset: Int,
                    prefix: String,
                    matchedEnclosuresIndices: Set<Int>? = null): CheckResult
}