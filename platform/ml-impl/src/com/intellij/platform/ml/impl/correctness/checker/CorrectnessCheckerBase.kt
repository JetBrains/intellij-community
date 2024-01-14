// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.codeInspection.InspectionEngine
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.SyntaxTraverser
import com.intellij.util.PairProcessor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
open class CorrectnessCheckerBase(private val semanticCheckers: List<SemanticChecker> = emptyList()) : CorrectnessChecker {
  @Suppress("PropertyName")
  protected val LOG = thisLogger()

  protected open fun buildPsiForSemanticChecks(file: PsiFile, suggestion: String, offset: Int, prefix: String): PsiFile {
    return file
  }

  private val toolWrappers = semanticCheckers.filterIsInstance<InspectionBasedSemanticChecker>()
    .map { it.toolWrapper }

  private val toolNameToSemanticChecker = semanticCheckers.filterIsInstance<InspectionBasedSemanticChecker>()
    .associateBy { it.toolWrapper.id }

  private val customSemanticCheckers = semanticCheckers.filterIsInstance<CustomSemanticChecker>()

  private val rawSemanticCheckers = semanticCheckers.filterIsInstance<RawSemanticChecker>()

  final override fun checkSemantic(file: PsiFile, suggestion: String, offset: Int, prefix: String): List<CorrectnessError> {
    if (semanticCheckers.isEmpty()) {
      return emptyList()
    }
    val fullPsi = buildPsiForSemanticChecks(file, suggestion, offset, prefix)

    val range = TextRange(offset - prefix.length, offset + suggestion.length - prefix.length)

    val elements = SyntaxTraverser.psiTraverser(fullPsi)
      .onRange(range)
      .toList()

    return findInspectionErrors(this.toolWrappers, this.toolNameToSemanticChecker, fullPsi, elements, file, offset, prefix, suggestion) +
           findCustomErrors(elements, file, offset, prefix, suggestion) +
           findRawErrors(fullPsi, range, file, offset, prefix, suggestion)
  }

  private fun findCustomErrors(elements: List<PsiElement>,
                               file: PsiFile,
                               offset: Int,
                               prefix: String,
                               suggestion: String): List<CorrectnessError> {
    return customSemanticCheckers.flatMap { analyzer ->
      elements.flatMap { element -> analyzer.findErrors(file, element, offset, prefix, suggestion) }
    }
  }

  private fun findRawErrors(file: PsiFile,
                            range: TextRange,
                            originalFile: PsiFile,
                            offset: Int,
                            prefix: String,
                            suggestion: String): List<CorrectnessError> {
    return rawSemanticCheckers.flatMap { analyzer ->
      analyzer.findErrors(file, range, originalFile, offset, prefix, suggestion)
    }
  }

  companion object {
    fun findInspectionErrors(toolWrappers: List<LocalInspectionToolWrapper>,
                             toolNameToSemanticChecker: Map<String, InspectionBasedSemanticChecker>,
                             fullPsi: PsiFile,
                             elements: List<PsiElement>,
                             file: PsiFile,
                             offset: Int,
                             prefix: String,
                             suggestion: String): List<CorrectnessError> {
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
      }
    }
  }
}
