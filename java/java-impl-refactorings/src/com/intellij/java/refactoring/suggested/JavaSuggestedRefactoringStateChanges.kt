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
  override fun createInitialState(anchor: PsiElement): SuggestedRefactoringState? {
    val state = super.createInitialState(anchor) ?: return null
    if (anchor is PsiMember && isDuplicate(anchor, state.oldSignature)) return null
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

  override fun findDeclaration(anchor: PsiElement): PsiElement? {
    return when (anchor) {
      is PsiCallExpression -> anchor.resolveMethod()
      else -> anchor
    }
  }

  override fun signature(anchor: PsiElement, prevState: SuggestedRefactoringState?): Signature? {
    if (anchor is PsiCallExpression) {
      return signatureFromCall(anchor)
    }
    val declaration = anchor as PsiNameIdentifierOwner
    val name = declaration.name ?: return null
    if (declaration !is PsiMethod) {
      return Signature.create(name, null, emptyList(), null)
    }

    val signature = declaration.signature() ?: return null

    return if (prevState == null) signature else matchParametersWithPrevState(signature, declaration, prevState)
  }

  private fun signatureFromCall(anchor: PsiCallExpression): Signature? {
    val expressions = anchor.argumentList!!.expressions
    val args = expressions.map { ex -> ex.text }
    val name = when (anchor) {
                 is PsiNewExpression -> anchor.classReference?.referenceName
                 is PsiMethodCallExpression -> anchor.methodExpression.referenceName
                 else -> null
               } ?: return null
    return Signature.create(name, null, listOf(), JavaCallAdditionalData(args))
  }

  override fun parameterMarkerRanges(anchor: PsiElement): List<TextRange?> {
    if (anchor !is PsiMethod) return emptyList()
    return anchor.parameterList.parameters.map { it.typeElement?.textRange }
  }
}

private fun PsiParameter.extractParameterData(): Parameter? {
  return Parameter(
    Any(),
    name,
    (typeElement ?: return null).text,
    JavaParameterAdditionalData(extractAnnotations())
  )
}

internal fun PsiMethod.signature(): Signature? {
  val visibility = this.visibility()
  val parameters = this.parameterList.parameters.map { it.extractParameterData() ?: return null }
  val annotations = this.extractAnnotations()
  val exceptions = this.extractExceptions()
  return Signature.create(
    this.name,
    this.returnTypeElement?.text,
    parameters,
    JavaDeclarationAdditionalData(visibility, annotations, exceptions)
  )
}
