package com.intellij.platform.ml.impl.correctness.finalizer

import com.intellij.lang.Language
import com.intellij.openapi.application.runReadAction
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.ScheduledForRemoval
@Deprecated("Do not use it")
abstract class SuggestionFinalizerBase(val language: Language) : SuggestionFinalizer {

  /**
   * Returns the order in which the elements will be traversed to finalize.
   */
  protected abstract fun getTraverseOrder(insertedElement: PsiElement): Sequence<PsiElement>

  /**
   * Returns the finalization for the element.
   */
  protected abstract fun getFinalizationCandidate(element: PsiElement): String?


  /**
   * This implementation traverse tree with the inserted [suggestion] according to [getTraverseOrder]
   * and appends text from [getFinalizationCandidate].
   */
  final override fun getFinalization(originalPsi: PsiFile, suggestion: String, offset: Int, prefix: String): FinalizedFile = runReadAction {
    require(suggestion.isNotBlank())
    val psi = PsiFileFactory.getInstance(originalPsi.project)
      .createFileFromText(language, originalPsi.text.take(offset - prefix.length) + suggestion)
    val insertedElement = psi.findElementAt(psi.textLength - 1 - suggestion.takeLastWhile { it == ' ' }.length)!!
    val finalization = getTraverseOrder(insertedElement).joinToString(separator = "") {
      getFinalizationCandidate(it).orEmpty()
    }
    val finalizedText = psi.text + finalization
    val finalizedPsi = PsiFileFactory.getInstance(originalPsi.project).createFileFromText(language, finalizedText)
    FinalizedFile(finalizedPsi)
  }
}