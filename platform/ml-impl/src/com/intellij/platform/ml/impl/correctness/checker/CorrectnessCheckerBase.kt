// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.codeInspection.InspectionEngine
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.PairProcessor
import com.intellij.platform.ml.impl.correctness.finalizer.SuggestionFinalizer
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class CorrectnessCheckerBase(open val semanticCheckers: List<SemanticChecker> = emptyList()) : CorrectnessChecker {
  @Suppress("PropertyName")
  protected val LOG = thisLogger()

  protected open val suggestionFinalizer: SuggestionFinalizer? = null

  final override fun checkSyntax(file: PsiFile,
                                 suggestion: String,
                                 offset: Int,
                                 prefix: String,
                                 ignoreSyntaxErrorsBeforeSuggestionLen: Int): List<CorrectnessError> {
    // todo: consider using length in leaves instead of plain offset
    val isSyntaxCorrect = suggestionFinalizer
                            ?.getFinalization(file, suggestion, offset, prefix)
                            ?.hasNoErrorsStartingFrom(offset - ignoreSyntaxErrorsBeforeSuggestionLen) ?: true
    return if (isSyntaxCorrect) {
      emptyList()
    }
    else {
      listOf(CorrectnessError(TextRange.EMPTY_RANGE, Severity.CRITICAL)) // todo specify error location
    }
  }

  protected open fun buildPsiForSemanticChecks(file: PsiFile, suggestion: String, offset: Int, prefix: String): PsiFile {
    return file
  }

  private val toolWrappers = semanticCheckers.filterIsInstance<InspectionBasedSemanticChecker>()
    .map { it.toolWrapper }

  private val toolNameToSemanticChecker = semanticCheckers.filterIsInstance<InspectionBasedSemanticChecker>()
    .associateBy { it.toolWrapper.id }

  private val customSemanticCheckers = semanticCheckers.filterIsInstance<CustomSemanticChecker>()

  final override fun checkSemantic(file: PsiFile, suggestion: String, offset: Int, prefix: String): List<CorrectnessError> {
    if (semanticCheckers.isEmpty()) {
      return emptyList()
    }
    val fullPsi = buildPsiForSemanticChecks(file, suggestion, offset, prefix)

    val range = TextRange(offset - prefix.length, offset + suggestion.length - prefix.length)

    val elements = SyntaxTraverser.psiTraverser(fullPsi)
      .onRange(range)
      .toList()

    return InspectionEngine.inspectElements(
      toolWrappers,
      fullPsi,
      fullPsi.textRange,
      true,
      true,
      ProgressManager.getInstance().progressIndicator,
      elements,
      PairProcessor.alwaysTrue()
    ).flatMap {
      val semanticChecker = checkNotNull(toolNameToSemanticChecker[it.key.id])
      semanticChecker.convertInspectionsResults(file, it.value, offset, prefix, suggestion)
    } + customSemanticCheckers.flatMap { analyzer ->
      elements.flatMap { element -> analyzer.findErrors(file, element, offset, prefix, suggestion) }
    }
  }
}
