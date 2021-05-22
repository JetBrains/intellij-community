// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.*
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions

class DefaultMethodExtractor: InplaceExtractMethodProvider {

  override fun extract(parameters: ExtractParameters): Pair<PsiMethod, PsiMethodCallExpression> {
    val extractOptions = findExtractOptions(parameters)
    val (callElements, method) = MethodExtractor().extractMethod(extractOptions)
    val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java, false)!!
    return Pair(method, callExpression)
  }

  override fun extractInDialog(parameters: ExtractParameters) {
    val extractOptions = findExtractOptions(parameters)
    MethodExtractor().doDialogExtract(extractOptions)
  }

  private fun findExtractOptions(parameters: ExtractParameters): ExtractOptions {
    val elements = ExtractSelector().suggestElementsToExtract(parameters.targetClass.containingFile, parameters.range)
    val analyzer = CodeFragmentAnalyzer(elements)
    var options = findExtractOptions(elements).copy(methodName = parameters.methodName)
    options = ExtractMethodPipeline.withTargetClass(analyzer, options, parameters.targetClass) ?: throw IllegalStateException("Failed to set target class")
    options = if (parameters.static) ExtractMethodPipeline.withForcedStatic(analyzer, options)!! else options
    return options
  }
}