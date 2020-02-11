// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

class JavaSuggestedRefactoringStateChanges(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringStateChanges(refactoringSupport) {
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