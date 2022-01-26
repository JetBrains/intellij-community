// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ChildRole
import com.intellij.psi.impl.source.tree.java.MethodElement
import com.intellij.refactoring.suggested.SuggestedChangeSignatureData
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.siyeh.ig.psiutils.TypeUtils

class JavaSuggestedRefactoringSupport : SuggestedRefactoringSupport {
  override fun isAnchor(psiElement: PsiElement): Boolean {
    if (psiElement is PsiCallExpression) {
      return psiElement.argumentList != null
    }
    if (psiElement !is PsiNameIdentifierOwner) return false
    if (psiElement is PsiParameter && psiElement.parent is PsiParameterList && psiElement.parent.parent is PsiMethod) return false
    return true
  }

  override fun signatureRange(anchor: PsiElement): TextRange? {
    if (anchor is PsiCallExpression) {
      return anchor.argumentList!!.textRange
    }
    val nameIdentifier = (anchor as PsiNameIdentifierOwner).nameIdentifier ?: return null
    return when (anchor) {
      is PsiMethod -> {
        val startOffset = anchor.modifierList.startOffset
        val semicolon = (anchor.node as MethodElement).findChildByRole(ChildRole.CLOSING_SEMICOLON)
        val endOffset = semicolon?.startOffset ?: anchor.body?.startOffset ?: anchor.endOffset
        TextRange(startOffset, endOffset)
      }

      else -> nameIdentifier.textRange
    }
  }

  override fun importsRange(psiFile: PsiFile): TextRange {
    return (psiFile as PsiJavaFile).importList!!.textRange
  }

  override fun nameRange(anchor: PsiElement): TextRange? {
    return (when (anchor) {
      is PsiMethodCallExpression -> anchor.methodExpression.referenceNameElement
      is PsiNewExpression -> anchor.classOrAnonymousClassReference
      is PsiNameIdentifierOwner -> anchor.nameIdentifier
      else -> null
    })?.textRange
  }

  override fun isIdentifierStart(c: Char) = c.isJavaIdentifierStart()
  override fun isIdentifierPart(c: Char) = c.isJavaIdentifierPart()

  override val stateChanges = JavaSuggestedRefactoringStateChanges(this)
  override val availability = JavaSuggestedRefactoringAvailability(this)
  override val ui get() = JavaSuggestedRefactoringUI
  override val execution = JavaSuggestedRefactoringExecution(this)

  companion object {
    fun extractAnnotationsToCopy(type: PsiType, owner: PsiModifierListOwner, file: PsiFile): List<PsiAnnotation> {
      val applicableAnnotations = (owner.modifierList ?: return emptyList()).applicableAnnotations
      if (applicableAnnotations.isEmpty()) return type.annotations.asList()

      val annotationNamesToCopy = OverrideImplementsAnnotationsHandler.EP_NAME.extensionList
        .flatMap { it.getAnnotations(file).asList() }
        .toSet()

      return mutableListOf<PsiAnnotation>().apply {
        for (annotation in applicableAnnotations) {
          val qualifiedName = annotation.qualifiedName ?: continue
          if (qualifiedName in annotationNamesToCopy && !type.hasAnnotation(qualifiedName)) {
            add(annotation)
          }
        }

        addAll(type.annotations)
      }
    }

  }
}

data class JavaParameterAdditionalData(
  val annotations: String,
  val defaultValue: String = ""
) : SuggestedRefactoringSupport.ParameterAdditionalData

interface JavaSignatureAdditionalData : SuggestedRefactoringSupport.SignatureAdditionalData {
  val visibility: String?
  val annotations: String
  val exceptionTypes: List<String>
}

data class JavaDeclarationAdditionalData(
  override val visibility: String?,
  override val annotations: String,
  override val exceptionTypes: List<String>
) : JavaSignatureAdditionalData

internal data class JavaCallAdditionalData(
  val origArguments: List<String>
) : SuggestedRefactoringSupport.SignatureAdditionalData

internal val SuggestedRefactoringSupport.Parameter.annotations: String
  get() = (additionalData as JavaParameterAdditionalData?)?.annotations ?: ""

internal val SuggestedRefactoringSupport.Signature.visibility: String?
  get() = (additionalData as? JavaSignatureAdditionalData)?.visibility

internal val SuggestedRefactoringSupport.Signature.annotations: String
  get() = (additionalData as JavaSignatureAdditionalData?)?.annotations ?: ""

internal val SuggestedRefactoringSupport.Signature.exceptionTypes: List<String>
  get() = (additionalData as JavaSignatureAdditionalData?)?.exceptionTypes ?: emptyList()

internal val SuggestedRefactoringSupport.Signature.origArguments: List<String>?
  get() = (additionalData as? JavaCallAdditionalData)?.origArguments

private val visibilityModifiers = listOf(PsiModifier.PUBLIC, PsiModifier.PROTECTED, PsiModifier.PACKAGE_LOCAL, PsiModifier.PRIVATE)

internal fun PsiMethod.visibility(): String? {
  return visibilityModifiers.firstOrNull { hasModifierProperty(it) }
}

internal fun PsiJvmModifiersOwner.extractAnnotations(): String {
  return annotations.joinToString(separator = " ") { it.text } //TODO: skip comments and spaces
}

internal fun PsiMethod.extractExceptions(): List<String> {
  return throwsList.referenceElements.map { it.text }
}

internal fun SuggestedChangeSignatureData.correctParameterTypes(origTypes: List<PsiType>): List<PsiType> {
  val anchor = this.anchor
  return if (anchor is PsiCallExpression) {
    // From call site
    val expressions = anchor.argumentList!!.expressions
    this.newSignature.parameters.mapIndexed { idx, param ->
      val oldParam = this.oldSignature.parameterById(param.id)
      if (oldParam != null) {
        origTypes[this.oldSignature.parameterIndex(oldParam)]
      }
      else {
        expressions[idx].type ?: TypeUtils.getObjectType(this.declaration)
      }
    }
  }
  else origTypes
}
