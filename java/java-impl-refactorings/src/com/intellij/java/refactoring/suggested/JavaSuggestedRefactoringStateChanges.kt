// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.openapi.project.DumbService
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.codeStyle.VariableKind
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import com.siyeh.ig.psiutils.VariableNameGenerator

class JavaSuggestedRefactoringStateChanges(refactoringSupport: SuggestedRefactoringSupport) :
  SuggestedRefactoringStateChanges(refactoringSupport) {
  override fun createInitialState(anchor: PsiElement): SuggestedRefactoringState? {
    val state = super.createInitialState(anchor) ?: return null
    val declaration = state.declaration
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

  override fun findDeclaration(state: SuggestedRefactoringState?, anchor: PsiElement): PsiElement? {
    return when (anchor) {
      is PsiCallExpression -> state?.declaration ?: if (DumbService.isDumb(anchor.project)) null else anchor.resolveMethod()
      else -> anchor
    }
  }

  override fun signature(anchor: PsiElement, prevState: SuggestedRefactoringState?): Signature? {
    if (anchor is PsiCallExpression) {
      return signatureFromCall(anchor, prevState)
    }
    val declaration = anchor as PsiNameIdentifierOwner
    val name = declaration.name ?: return null
    if (declaration !is PsiMethod) {
      return Signature.create(name, null, emptyList(), null)
    }

    val visibility = declaration.visibility()
    val parameters = declaration.parameterList.parameters.map { it.extractParameterData() ?: return null }
    val annotations = declaration.extractAnnotations()
    val exceptions = declaration.extractExceptions()
    val signature = Signature.create(
      name,
      declaration.returnTypeElement?.text,
      parameters,
      JavaDeclarationAdditionalData(visibility, annotations, exceptions)
    ) ?: return null

    return if (prevState == null) signature else matchParametersWithPrevState(signature, declaration, prevState)
  }

  private fun signatureFromCall(anchor: PsiCallExpression, prevState: SuggestedRefactoringState?): Signature? {
    val expressions = anchor.argumentList!!.expressions
    val args = expressions.map { ex -> ex.text }
    if (prevState == null) {
      val resolveResult = anchor.resolveMethodGenerics()
      if (!resolveResult.isValidResult) return null
      val method = resolveResult.element as? PsiMethod ?: return null
      if (method is PsiCompiledElement) return null
      // TODO: support vararg methods
      if (method.isVarArgs) return null
      val parameters = method.parameterList.parameters.map { it.extractParameterData() ?: return null }
      if (parameters.size != args.size) return null
      return Signature.create(method.name, method.returnTypeElement?.text, parameters,
        JavaCallAdditionalData(args, method))
    }
    val oldSignature = prevState.oldSignature
    val method = prevState.declaration as? PsiMethod ?: return null
    val origArgs = ArrayList(oldSignature.origArguments ?: return null)
    val newParams = args.mapIndexed { idx, argText ->
      val origIdx = origArgs.indexOf(argText)
      if (origIdx >= 0) {
        origArgs[origIdx] = null
        oldSignature.parameters[origIdx]
      } else {
        val newArg = expressions[idx]
        val type = newArg.type
        val name = VariableNameGenerator(method, VariableKind.PARAMETER).byExpression(newArg).byType(type).generate(true)
        Parameter(Any(), name, type?.presentableText ?: "Object", JavaParameterAdditionalData("", newArg.text))
      }
    }
    return Signature.create(oldSignature.name, oldSignature.type, newParams, oldSignature.additionalData)
  }

  override fun parameterMarkerRanges(anchor: PsiElement): List<TextRange?> {
    if (anchor is PsiCallExpression) {
      return anchor.argumentList!!.expressions.map { null }
    }
    if (anchor !is PsiMethod) return emptyList()
    return anchor.parameterList.parameters.map { it.typeElement?.textRange }
  }

  private fun PsiParameter.extractParameterData(): Parameter? {
    return Parameter(
      Any(),
      name,
      (typeElement ?: return null).text,
      JavaParameterAdditionalData(extractAnnotations())
    )
  }
}