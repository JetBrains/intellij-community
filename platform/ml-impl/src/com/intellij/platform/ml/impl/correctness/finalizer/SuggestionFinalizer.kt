package com.intellij.platform.ml.impl.correctness.finalizer

import com.intellij.platform.ml.impl.correctness.finalizer.FinalizedFile
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.ScheduledForRemoval

@ScheduledForRemoval
@Deprecated("Do not use it")
interface SuggestionFinalizer {
  /**
   * This function takes the text up to the caret, inserts the [suggestion] into it
   * and tries to finalize the file so that the inserted [suggestion] does not create syntax errors
   * (due to unfinished code in the [suggestion]).
   *
   * Example:
   * If we have a context
   * ```
   * for i
   * ```
   * And suggestion
   * ```
   * in range(
   * ```
   * Then the [FinalizedFile] likely will be
   * ```
   * for i in range():
   *  pass
   * ```
   *
   * Please see [FinalizedFile] if you are going to change the semantics of this function.
   */
  fun getFinalization(originalPsi: PsiFile, suggestion: String, offset: Int, prefix: String): FinalizedFile
}