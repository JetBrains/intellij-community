// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

class JavaSuggestedRefactoringStateChanges(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringStateChanges(refactoringSupport) {
  override fun createInitialState(declaration: PsiElement): SuggestedRefactoringState? {
    val state = super.createInitialState(declaration) ?: return null
    if (declaration is PsiMember && isDuplicate(declaration, state.oldSignature)) return null
    return state
  }

  private fun isDuplicate(member: PsiMember, signature: Signature): Boolean {
    val psiClass = member.containingClass ?: return false
    val name = member.name!!
    when (member) {
      is PsiMethod -> {
        return psiClass.findMethodsByName(name, false)
          .any {
            if (it == member) return@any false
            val otherSignature = signature(it, null) ?: return@any false
            areDuplicateSignatures(otherSignature, signature)
          }
      }

      is PsiField -> {
        return psiClass.fields.any { it != member && it.name == name }
      }

      else -> return false
    }
  }

  // we can't compare signatures by equals here because it takes into account parameter id's and they will be different in our case
  private fun areDuplicateSignatures(signature1: Signature, signature2: Signature): Boolean {
    if (signature1.name != signature2.name) return false
    if (signature1.type != signature2.type) return false
    if (signature1.parameters.size != signature2.parameters.size) return false
    return signature1.parameters.zip(signature2.parameters).all { (p1, p2) ->
      p1.type == p2.type && p1.name == p2.name
    }
  }

  override fun signature(declaration: PsiElement, prevState: SuggestedRefactoringState?): Signature? {
    declaration as PsiNameIdentifierOwner
    val name = declaration.name ?: return null
    if (declaration !is PsiMethod) {
      return Signature.create(name, null, emptyList(), null)
    }

    val visibility = declaration.visibility()
    val parameters = declaration.parameterList.parameters.map { it.extractParameterData() ?: return null }
    val annotations = declaration.extractAnnotations()
    val exceptions = declaration.throwsList.referenceElements.map { it.text }
    val signature = Signature.create(
      name,
      declaration.returnTypeElement?.text,
      parameters,
      JavaSignatureAdditionalData(visibility, annotations, exceptions)
    ) ?: return null

    return if (prevState == null) signature else matchParametersWithPrevState(signature, declaration, prevState)
  }

  override fun parameterMarkerRanges(declaration: PsiElement): List<TextRange?> {
    if (declaration !is PsiMethod) return emptyList()
    return declaration.parameterList.parameters.map { it.typeElement?.textRange }
  }

  private fun PsiParameter.extractParameterData(): Parameter? {
    return Parameter(
      Any(),
      name,
      (typeElement ?: return null).text,
      JavaParameterAdditionalData(extractAnnotations())
    )
  }

  private fun PsiJvmModifiersOwner.extractAnnotations(): String {
    return annotations.joinToString(separator = " ") { it.text } //TODO: skip comments and spaces
  }

  private val visibilityModifiers = listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)

  private fun PsiMethod.visibility(): String? {
    return visibilityModifiers.firstOrNull { hasModifierProperty(it) }
  }
}