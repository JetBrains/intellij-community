// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.psi.*
import com.intellij.refactoring.suggested.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

class JavaSuggestedRefactoringAvailability(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringAvailability(refactoringSupport)
{
  // we use resolve to filter out annotations that we don't want to spread over hierarchy
  override fun refineSignaturesWithResolve(state: SuggestedRefactoringState): SuggestedRefactoringState {
    val declaration = state.declaration as? PsiMethod ?: return state
    val restoredDeclarationCopy = state.createRestoredDeclarationCopy(refactoringSupport) as PsiMethod
    val psiFile = declaration.containingFile
    val oldSignature = extractAnnotationsWithResolve(state.oldSignature, restoredDeclarationCopy, psiFile)
    val newSignature = extractAnnotationsWithResolve(state.newSignature, declaration, psiFile)
    return state.copy(oldSignature = oldSignature, newSignature = newSignature)
  }

  override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
    val declaration = state.declaration
    val oldSignature = state.oldSignature
    val newSignature = state.newSignature

    if (declaration !is PsiMethod) {
      return SuggestedRenameData(declaration as PsiNamedElement, oldSignature.name)
    }

    val updateUsagesData = SuggestedChangeSignatureData.create(state, USAGES)

    if (hasParameterAddedRemovedOrReordered(oldSignature, newSignature)) return updateUsagesData

    val updateOverridesData = if (declaration.canHaveOverrides(oldSignature, newSignature))
      updateUsagesData.copy(
        nameOfStuffToUpdate = if (declaration.hasModifierProperty(PsiModifier.ABSTRACT)) IMPLEMENTATIONS else OVERRIDES
      )
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

  private fun PsiMethod.canHaveOverrides(oldSignature: Signature, newSignature: Signature): Boolean {
    if (isConstructor) return false
    if (oldSignature.visibility == PsiModifier.PRIVATE || newSignature.visibility == PsiModifier.PRIVATE) return false
    if (hasModifierProperty(PsiModifier.STATIC) || hasModifierProperty(PsiModifier.FINAL)) return false
    if ((containingClass ?: return false).hasModifierProperty(PsiModifier.FINAL)) return false
    return true
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