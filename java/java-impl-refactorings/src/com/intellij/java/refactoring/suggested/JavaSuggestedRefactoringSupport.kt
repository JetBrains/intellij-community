// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.*
import com.intellij.psi.impl.source.tree.ChildRole
import com.intellij.psi.impl.source.tree.java.MethodElement
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset

class JavaSuggestedRefactoringSupport : SuggestedRefactoringSupport {
  override fun isDeclaration(psiElement: PsiElement): Boolean {
    if (psiElement !is PsiNameIdentifierOwner) return false
    if (psiElement is PsiParameter && psiElement.parent is PsiParameterList && psiElement.parent.parent is PsiMethod) return false
    return true
  }

  override fun signatureRange(declaration: PsiElement): TextRange? {
    val nameIdentifier = (declaration as PsiNameIdentifierOwner).nameIdentifier ?: return null
    return when (declaration) {
      is PsiMethod -> {
        val startOffset = declaration.modifierList.startOffset
        val semicolon = (declaration.node as MethodElement).findChildByRole(ChildRole.CLOSING_SEMICOLON)
        val endOffset = semicolon?.startOffset ?: declaration.body?.startOffset ?: declaration.endOffset
        TextRange(startOffset, endOffset)
      }

      else -> nameIdentifier.textRange
    }
  }

  override fun importsRange(psiFile: PsiFile): TextRange {
    return (psiFile as PsiJavaFile).importList!!.textRange
  }

  override fun nameRange(declaration: PsiElement): TextRange? {
    return (declaration as PsiNameIdentifierOwner).nameIdentifier?.textRange
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
  val annotations: String
) : SuggestedRefactoringSupport.ParameterAdditionalData

data class JavaSignatureAdditionalData(
  val visibility: String?,
  val annotations: String,
  val exceptionTypes: List<String>
) : SuggestedRefactoringSupport.SignatureAdditionalData

internal val SuggestedRefactoringSupport.Parameter.annotations: String
  get() = (additionalData as JavaParameterAdditionalData?)?.annotations ?: ""

internal val SuggestedRefactoringSupport.Signature.visibility: String?
  get() = (additionalData as JavaSignatureAdditionalData?)?.visibility

internal val SuggestedRefactoringSupport.Signature.annotations: String
  get() = (additionalData as JavaSignatureAdditionalData?)?.annotations ?: ""

internal val SuggestedRefactoringSupport.Signature.exceptionTypes: List<String>
  get() = (additionalData as JavaSignatureAdditionalData?)?.exceptionTypes ?: emptyList()
