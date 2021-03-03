// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import com.intellij.refactoring.extractMethod.newImpl.ExtractSelector
import com.intellij.refactoring.util.duplicates.DuplicatesImpl

class LegacyMethodExtractor: InplaceExtractMethodProvider {

  var processor: ExtractMethodProcessor? = null

  override fun extract(parameters: ExtractParameters): Pair<PsiMethod, PsiMethodCallExpression> {
    val project = parameters.targetClass.project
    val elements = ExtractSelector().suggestElementsToExtract(parameters.targetClass.containingFile, parameters.range)
    val processor = ExtractMethodHandler.getProcessor(project, elements.toTypedArray(), parameters.targetClass.containingFile, false)
    processor ?: throw IllegalStateException("Failed to create processor for selected elements")
    processor.prepare()
    processor.prepareVariablesAndName()
    processor.methodName = parameters.methodName
    processor.targetClass = parameters.targetClass
    processor.isStatic = parameters.static
    processor.prepareNullability()
    ExtractMethodHandler.extractMethod(project, processor)
    val method = processor.extractedMethod
    val call = findSingleMethodCall(method)!!
    this.processor = processor
    return Pair(method, call)
  }

  override fun extractInDialog(parameters: ExtractParameters) {
    val project = parameters.targetClass.project
    val elements = ExtractSelector().suggestElementsToExtract(parameters.targetClass.containingFile, parameters.range)
    val processor = ExtractMethodHandler.getProcessor(project, elements.toTypedArray(), parameters.targetClass.containingFile, false)
    if (processor != null) {
      ExtractMethodHandler.invokeOnElements(project, processor, parameters.targetClass.containingFile, false)
    }
  }

  override fun postprocess(editor: Editor, method: PsiMethod) {
    val handler = processor ?: return
    val project = editor.project ?: return
    val methodCall = findSingleMethodCall(method) ?: return
    handler.extractedMethod = method
    handler.methodCall = methodCall
    handler.methodName = method.name
    handler.parametrizedDuplicates?.apply {
      setParametrizedMethod(method)
      setParametrizedCall(methodCall)
    }
    DuplicatesImpl.processDuplicates(handler, project, editor)
  }

  private fun findSingleMethodCall(method: PsiMethod): PsiMethodCallExpression? {
    val reference = MethodReferencesSearch.search(method).single().element
    return PsiTreeUtil.getParentOfType(reference, PsiMethodCallExpression::class.java, false)
  }
}