// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.newImpl.CodeFragmentAnalyzer
import com.intellij.refactoring.extractMethod.newImpl.ExtractMethodPipeline
import com.intellij.refactoring.extractMethod.newImpl.MethodExtractor
import com.intellij.refactoring.extractMethod.newImpl.findExtractOptions
import com.intellij.refactoring.extractMethod.newImpl.structures.ExtractOptions

class DefaultMethodExtractor: InplaceExtractMethodProvider {

  override fun extract(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): Pair<PsiMethod, PsiMethodCallExpression> {
    val extractOptions = findExtractOptions(targetClass, elements, methodName, makeStatic)
    val (callElements, method) = MethodExtractor().extractMethod(extractOptions)
    val callExpression = PsiTreeUtil.findChildOfType(callElements.first(), PsiMethodCallExpression::class.java, false)!!
    return Pair(method, callExpression)
  }

  override fun extractInDialog(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean) {
    val extractOptions = findExtractOptions(targetClass, elements, methodName, makeStatic)
    MethodExtractor().doDialogExtract(extractOptions)
  }

  private fun findExtractOptions(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): ExtractOptions {
    val analyzer = CodeFragmentAnalyzer(elements)
    var options = findExtractOptions(elements).copy(methodName = methodName)
    options = ExtractMethodPipeline.withTargetClass(analyzer, options, targetClass) ?: throw IllegalStateException("Failed to set target class")
    options = if (makeStatic) ExtractMethodPipeline.withForcedStatic(analyzer, options)!! else options
    return options
  }
}