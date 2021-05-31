// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl.inplace

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.search.searches.MethodReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.extractMethod.ExtractMethodHandler
import com.intellij.refactoring.extractMethod.ExtractMethodProcessor
import com.intellij.refactoring.util.duplicates.DuplicatesImpl

class LegacyMethodExtractor: InplaceExtractMethodProvider {

  var processor: ExtractMethodProcessor? = null

  override fun extract(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean): Pair<PsiMethod, PsiMethodCallExpression> {
    val project = targetClass.project
    val processor = ExtractMethodHandler.getProcessor(project, elements.toTypedArray(), targetClass.containingFile, false)
    processor ?: throw IllegalStateException("Failed to create processor for selected elements")
    processor.prepare()
    processor.prepareVariablesAndName()
    processor.methodName = methodName
    processor.targetClass = targetClass
    processor.isStatic = makeStatic
    processor.prepareNullability()
    ExtractMethodHandler.extractMethod(project, processor)
    val method = processor.extractedMethod
    val call = findSingleMethodCall(method)!!
    this.processor = processor
    return Pair(method, call)
  }

  override fun extractInDialog(targetClass: PsiClass, elements: List<PsiElement>, methodName: String, makeStatic: Boolean) {
    val project = targetClass.project
    val file = targetClass.containingFile
    val processor = ExtractMethodHandler.getProcessor(project, elements.toTypedArray(), file, false)
    if (processor != null) {
      ExtractMethodHandler.invokeOnElements(project, processor, file, false)
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
      val nameIdentifier = method.nameIdentifier ?: return
      parametrizedMethod?.nameIdentifier?.replace(nameIdentifier)
      parametrizedCall?.methodExpression?.referenceNameElement?.replace(nameIdentifier)
    }
    DuplicatesImpl.processDuplicates(handler, project, editor)
  }

  private fun findSingleMethodCall(method: PsiMethod): PsiMethodCallExpression? {
    val reference = MethodReferencesSearch.search(method).single().element
    return PsiTreeUtil.getParentOfType(reference, PsiMethodCallExpression::class.java, false)
  }
}