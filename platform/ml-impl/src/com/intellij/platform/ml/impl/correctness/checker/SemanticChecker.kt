// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.impl.correctness.checker

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ex.LocalInspectionToolWrapper
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
sealed interface SemanticChecker {
  fun getLocationInSuggestion(errorRangeInFile: TextRange, offset: Int, prefix: String, suggestion: String): TextRange? {
    val shift = offset - prefix.length
    val suggestionLocationInFile = TextRange(0, suggestion.length).shiftRight(shift)
    if (!suggestionLocationInFile.intersects(errorRangeInFile)) {
      return null
    }
    return suggestionLocationInFile.intersection(errorRangeInFile)!!.shiftLeft(shift)
  }
}

@ApiStatus.Internal
abstract class InspectionBasedSemanticChecker(localInspectionTool: LocalInspectionTool) : SemanticChecker {
  abstract fun convertInspectionsResults(
    originalPsi: PsiFile,
    problemDescriptors: List<ProblemDescriptor>,
    offset: Int,
    prefix: String,
    suggestion: String
  ): List<CorrectnessError>

  val toolWrapper: LocalInspectionToolWrapper = LocalInspectionToolWrapper(localInspectionTool)

  protected fun getLocationInSuggestion(problemDescriptor: ProblemDescriptor, offset: Int, prefix: String, suggestion: String): TextRange? =
    getLocationInSuggestion(getErrorRangeInFile(problemDescriptor), offset, prefix, suggestion)

  protected fun getErrorRangeInFile(problemDescriptor: ProblemDescriptor): TextRange {
    val rangeInElement = problemDescriptor.textRangeInElement ?: TextRange(0, problemDescriptor.psiElement.textLength)
    return rangeInElement.shiftRight(problemDescriptor.psiElement.textRange.startOffset)
  }
}

@ApiStatus.Internal
abstract class CustomSemanticChecker : SemanticChecker {
  abstract fun findErrors(originalPsi: PsiFile,
                          element: PsiElement,
                          offset: Int,
                          prefix: String,
                          suggestion: String): List<CorrectnessError>
}

@ApiStatus.Internal
abstract class RawSemanticChecker : SemanticChecker {
  abstract fun findErrors(file: PsiFile,
                          range: TextRange,
                          originalFile: PsiFile,
                          offset: Int,
                          prefix: String,
                          suggestion: String): List<CorrectnessError>
}