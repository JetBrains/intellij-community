// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

class JavaSuggestedRefactoringAvailability(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringAvailability(refactoringSupport) {
  private val HAS_OVERRIDES = Key<Boolean>("JavaSuggestedRefactoringAvailability.HAS_OVERRIDES")
  private val HAS_USAGES = Key<Boolean>("JavaSuggestedRefactoringAvailability.HAS_USAGES")

  // disable refactoring suggestion for method which overrides another method
  override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
    if (state.declaration !is PsiMethod) return false
    val restoredDeclarationCopy = state.restoredDeclarationCopy()
    return restoredDeclarationCopy is PsiMethod && restoredDeclarationCopy.findSuperMethods().isNotEmpty()
  }

  override fun amendStateInBackground(state: SuggestedRefactoringState): Iterator<SuggestedRefactoringState> {
    return iterator {
      if (state.additionalData[HAS_OVERRIDES] == null) {
        val method = state.declaration as? PsiMethod
        if (method != null && method.canHaveOverrides(state.oldSignature)) {
          val restoredMethod = state.restoredDeclarationCopy() as PsiMethod
          val hasOverrides = OverridingMethodsSearch.search(restoredMethod, false).findFirst() != null
          yield(state.withAdditionalData(HAS_OVERRIDES, hasOverrides))
        }
      }

      if (state.additionalData[HAS_USAGES] == null) {
        val declarationCopy = state.restoredDeclarationCopy()
        val useScope = declarationCopy.useScope
        if (useScope is LocalSearchScope) {
          val hasUsages = ReferencesSearch.search(declarationCopy, useScope).findFirst() != null
          yield(state.withAdditionalData(HAS_USAGES, hasUsages))
        }
      }
    }
  }

  // we use resolve to filter out annotations that we don't want to spread over hierarchy
  override fun refineSignaturesWithResolve(state: SuggestedRefactoringState): SuggestedRefactoringState {
    val declaration = state.declaration as? PsiMethod ?: return state
    val restoredDeclarationCopy = state.restoredDeclarationCopy() as PsiMethod
    val psiFile = declaration.containingFile
    return state
      .withOldSignature(extractAnnotationsWithResolve(state.oldSignature, restoredDeclarationCopy, psiFile))
      .withNewSignature(extractAnnotationsWithResolve(state.newSignature, declaration, psiFile))
  }

  override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
    val declaration = state.declaration
    val oldSignature = state.oldSignature
    val newSignature = state.newSignature

    if (declaration !is PsiMethod) {
      if (state.additionalData[HAS_USAGES] == false) return null
      return SuggestedRenameData(declaration as PsiNamedElement, oldSignature.name)
    }

    val canHaveOverrides = declaration.canHaveOverrides(oldSignature) && state.additionalData[HAS_OVERRIDES] != false
    if (state.additionalData[HAS_USAGES] == false && !canHaveOverrides) return null

    val updateUsagesData = SuggestedChangeSignatureData.create(state, RefactoringBundle.message("suggested.refactoring.usages"))

    if (hasParameterAddedRemovedOrReordered(oldSignature, newSignature)) return updateUsagesData

    val updateOverridesData = if (canHaveOverrides)
      updateUsagesData.copy(nameOfStuffToUpdate = if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) RefactoringBundle.message(
        "suggested.refactoring.implementations")
      else RefactoringBundle.message("suggested.refactoring.overrides"))
    else
      null

    val (nameChanges, renameData) = nameChanges(oldSignature, newSignature, declaration, declaration.parameterList.parameters.asList())
    val methodNameChanged = oldSignature.name != newSignature.name

    if (hasTypeChanges(oldSignature, newSignature) || oldSignature.visibility != newSignature.visibility) {
      return if (methodNameChanged || nameChanges > 0 && declaration.body != null) updateUsagesData else updateOverridesData
    }

    return when {
      renameData != null -> renameData
      nameChanges > 0 -> if (methodNameChanged || declaration.body != null) updateUsagesData else updateOverridesData
      else -> null
    }
  }

  private fun PsiMethod.canHaveOverrides(oldSignature: Signature): Boolean {
    return PsiUtil.canBeOverridden(this) && oldSignature.visibility != PsiModifier.PRIVATE
  }

  override fun hasTypeChanges(oldSignature: Signature, newSignature: Signature): Boolean {
    return super.hasTypeChanges(oldSignature, newSignature)
           || oldSignature.annotations != newSignature.annotations
           || oldSignature.exceptionTypes != newSignature.exceptionTypes
  }

  override fun hasParameterTypeChanges(oldParam: Parameter, newParam: Parameter): Boolean {
    return super.hasParameterTypeChanges(oldParam, newParam) || oldParam.annotations != newParam.annotations
  }

  // Annotations were extracted without use of resolve. We must extract them again using more precise method.
  private fun extractAnnotationsWithResolve(signature: Signature, declaration: PsiMethod, psiFile: PsiFile): Signature {
    val psiParameters = declaration.parameterList.parameters
    require(signature.parameters.size == psiParameters.size)

    return Signature.create(
      signature.name,
      signature.type,
      signature.parameters.zip(psiParameters.asList()).map { (parameter, psiParameter) ->
        val annotations = extractAnnotations(psiParameter.type, psiParameter, psiFile)
        parameter.copy(additionalData = JavaParameterAdditionalData(annotations))
      },
      JavaSignatureAdditionalData(
        signature.visibility,
        extractAnnotations(declaration.returnType, declaration, psiFile),
        signature.exceptionTypes
      )
    )!!
  }

  private fun extractAnnotations(type: PsiType?, owner: PsiModifierListOwner, psiFile: PsiFile): String {
    if (type == null) return ""
    return JavaSuggestedRefactoringSupport.extractAnnotationsToCopy(type, owner, psiFile)
      .joinToString(separator = " ") { it.text } //TODO: strip comments and line breaks
  }
}