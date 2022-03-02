// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.OverridingMethodsSearch
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtil
import com.intellij.refactoring.RefactoringBundle
import com.intellij.refactoring.suggested.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.siyeh.ig.psiutils.VariableNameGenerator

class JavaSuggestedRefactoringAvailability(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringAvailability(refactoringSupport) {
  private val HAS_OVERRIDES = Key<Boolean>("JavaSuggestedRefactoringAvailability.HAS_OVERRIDES")
  private val HAS_USAGES = Key<Boolean>("JavaSuggestedRefactoringAvailability.HAS_USAGES")

  // disable refactoring suggestion for method which overrides another method
  override fun shouldSuppressRefactoringForDeclaration(state: SuggestedRefactoringState): Boolean {
    if (state.anchor !is PsiMethod && state.anchor !is PsiCallExpression) return false
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
        val useScope = declarationCopy?.useScope
        if (useScope is LocalSearchScope) {
          val hasUsages = ReferencesSearch.search(declarationCopy, useScope).findFirst() != null
          yield(state.withAdditionalData(HAS_USAGES, hasUsages))
        }
      }
    }
  }

  fun callStateToDeclarationState(state: SuggestedRefactoringState): SuggestedRefactoringState? {
    val anchor = state.anchor as? PsiCallExpression ?: return null
    val resolveResult = anchor.resolveMethodGenerics()
    if (resolveResult.isValidResult) return null
    val method = resolveResult.element as? PsiMethod ?: return null
    if (method is PsiCompiledElement) return null
    // TODO: support vararg methods
    if (method.isVarArgs) return null
    val expressions = anchor.argumentList!!.expressions
    val args = expressions.map { ex -> ex.text }
    val psiParameters = method.parameterList.parameters
    val oldSignature = state.oldSignature
    val origArgs = ArrayList((oldSignature.additionalData as? JavaCallAdditionalData)?.origArguments ?: return null)
    if (psiParameters.size != origArgs.size) return null
    val oldDeclarationSignature = method.signature() ?: return null
    val parameters = oldDeclarationSignature.parameters
    val newParams = args.mapIndexed { idx, argText ->
      val origIdx = origArgs.indexOf(argText)
      if (origIdx >= 0) {
        origArgs[origIdx] = null
        parameters[origIdx]
      }
      else {
        val newArg = expressions[idx]
        val type = newArg.type
        val name = VariableNameGenerator(method, VariableKind.PARAMETER).byExpression(newArg).byType(type).generate(true)
        Parameter(Any(), name, type?.presentableText ?: "Object", JavaParameterAdditionalData("", newArg.text))
      }
    }
    val newDeclarationSignature = Signature.create(
      oldDeclarationSignature.name, oldDeclarationSignature.type, newParams, oldDeclarationSignature.additionalData) ?: return null
    return state
      .withOldSignature(oldDeclarationSignature)
      .withNewSignature(newDeclarationSignature)
  }

  // we use resolve to filter out annotations that we don't want to spread over hierarchy
  override fun refineSignaturesWithResolve(state: SuggestedRefactoringState): SuggestedRefactoringState {
    val anchor = state.anchor
    if (anchor is PsiCallExpression) return state // TODO
    val declaration = anchor as? PsiMethod ?: return state
    val restoredDeclarationCopy = state.restoredDeclarationCopy() as PsiMethod
    val psiFile = declaration.containingFile
    return state
      .withOldSignature(extractAnnotationsWithResolve(state.oldSignature, restoredDeclarationCopy, psiFile))
      .withNewSignature(extractAnnotationsWithResolve(state.newSignature, declaration, psiFile))
  }

  override fun detectAvailableRefactoring(state: SuggestedRefactoringState): SuggestedRefactoringData? {
    var updatedState = state
    val whatToUpdate: String
    val declaration: PsiElement
    val anchor = state.anchor
    if (anchor is PsiCallExpression) {
      updatedState = callStateToDeclarationState(updatedState) ?: return null
      declaration = anchor.resolveMethod() ?: return null
      whatToUpdate = RefactoringBundle.message("suggested.refactoring.declaration")
    }
    else {
      whatToUpdate = RefactoringBundle.message("suggested.refactoring.usages")
      declaration = anchor
    }
    val oldSignature = updatedState.oldSignature
    val newSignature = updatedState.newSignature

    if (declaration !is PsiMethod) {
      if (updatedState.additionalData[HAS_USAGES] == false) return null
      return SuggestedRenameData(declaration as PsiNamedElement, oldSignature.name)
    }

    val canHaveOverrides = declaration.canHaveOverrides(oldSignature) && updatedState.additionalData[HAS_OVERRIDES] != false
    if (updatedState.additionalData[HAS_USAGES] == false && !canHaveOverrides) return null

    val updateUsagesData = SuggestedChangeSignatureData.create(updatedState, whatToUpdate)

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
      JavaDeclarationAdditionalData(
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