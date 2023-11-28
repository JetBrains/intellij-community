package com.intellij.platform.ml.impl.correctness.finalizer

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use it")
class FinalizedFile(finalizedPsi: PsiFile) {
  private val errorElementsRanges = SyntaxTraverser.psiTraverser(finalizedPsi)
    .filter(PsiErrorElement::class.java)
    .map { it.textRange }
    .toList()

  /**
   * This function checks that the finalized file does not contain errors starting with [offset] or later.
   *
   * Since when finalizing a file, we only add code after suggestion,
   * if you run it with [offset] = 0
   * it cannot return true if suggestion is syntactically incorrect.
   * This is an important property of this function, it must be preserved.
   *
   * Of course, if you run this function with a large offset, it will always say that there are no errors.
   * So you should run it with an offset less than the offset of the completion call.
   * But how much less depends on how many errors you need to ignore before completion.
   */
  fun hasNoErrorsStartingFrom(offset: Int): Boolean = errorElementsRanges.none { it.startOffset >= offset }
}