// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedContainingDeclaration
import com.intellij.codeInspection.AnnotatedApiUsageUtil.findAnnotatedTypeUsedInDeclarationSignature
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.deprecation.DeprecationInspection
import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UField
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.sourcePsiElement
import org.jetbrains.uast.toUElement

/**
 * Reports a usage of an API that an annotation marks as unstable.
 *
 * A subclass states which annotations it checks and which message it shows for each of them.
 */
@ApiStatus.Internal
abstract class UnstableApiUsageInspectionBase : LocalInspectionTool() {

  /**
   * The fully qualified names of the annotations that this inspection reports.
   */
  protected abstract val annotationsToCheck: List<String>

  /**
   * `true` skips a usage inside an import statement.
   */
  protected open val ignoreInsideImports: Boolean
    get() = true

  /**
   * `true` skips an API that this project declares itself.
   */
  protected open val ignoreApiDeclaredInThisProject: Boolean
    get() = true

  /**
   * Returns the provider of the message for the annotation [annotationFqn], or `null` to report nothing for it.
   */
  protected abstract fun getMessageProvider(annotationFqn: String): UnstableApiUsageMessageProvider?

  /**
   * Returns `true` to report nothing at [usage] for the API that [annotationFqn] marks as unstable.
   *
   * @param usage the element that the inspection highlights.
   */
  protected open fun isUsageIgnored(usage: PsiElement, annotationFqn: String): Boolean = false

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    val annotations = annotationsToCheck
    if (annotations.none { AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(it, holder.file) }) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return ApiUsageUastVisitor.createPsiElementVisitor(
      UnstableApiUsageProcessor(
        holder,
        ignoreInsideImports,
        ignoreApiDeclaredInThisProject,
        annotations,
        ::getMessageProvider,
        ::isUsageIgnored
      )
    )
  }
}

/**
 * Builds the message that [UnstableApiUsageInspectionBase] shows for one annotation.
 */
@ApiStatus.Internal
interface UnstableApiUsageMessageProvider {

  val problemHighlightType: ProblemHighlightType

  @InspectionMessage
  fun buildMessage(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  @InspectionMessage
  fun buildMessageUnstableMethodOverridden(annotatedContainingDeclaration: AnnotatedContainingDeclaration): String

  @InspectionMessage
  fun buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(
    referencedApi: PsiModifierListOwner,
    annotatedTypeUsedInSignature: AnnotatedContainingDeclaration
  ): String
}

private class UnstableApiUsageProcessor(
  private val problemsHolder: ProblemsHolder,
  private val ignoreInsideImports: Boolean,
  private val ignoreApiDeclaredInThisProject: Boolean,
  private val unstableApiAnnotations: List<String>,
  private val messageProviderByAnnotation: (String) -> UnstableApiUsageMessageProvider?,
  private val usageIgnored: (PsiElement, String) -> Boolean
) : ApiUsageProcessor {

  private companion object {
    fun isLibraryElement(element: PsiElement): Boolean {
      if (ApplicationManager.getApplication().isUnitTestMode) {
        return true
      }
      val containingVirtualFile = PsiUtilCore.getVirtualFile(element)
      return containingVirtualFile != null && ProjectFileIndex.getInstance(element.project).isInLibraryClasses(containingVirtualFile)
    }
  }

  override fun processImportReference(sourceNode: UElement, target: PsiModifierListOwner) {
    if (!ignoreInsideImports) {
      checkUnstableApiUsage(target, sourceNode, false)
    }
  }

  override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
    checkUnstableApiUsage(target, sourceNode, false)
  }

  override fun processConstructorInvocation(
    sourceNode: UElement,
    instantiatedClass: PsiClass,
    constructor: PsiMethod?,
    subclassDeclaration: UClass?
  ) {
    if (constructor != null) {
      checkUnstableApiUsage(constructor, sourceNode, false)
    }
  }

  override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
    checkUnstableApiUsage(overriddenMethod, method, true)
  }

  private fun getMessageProvider(psiAnnotation: PsiAnnotation): UnstableApiUsageMessageProvider? {
    val annotationName = psiAnnotation.qualifiedName ?: return null
    return messageProviderByAnnotation(annotationName)
  }

  private fun getElementToHighlight(sourceNode: UElement): PsiElement? =
    (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi

  private fun isUsageIgnored(declaration: AnnotatedContainingDeclaration, sourceNode: UElement): Boolean {
    val annotationFqn = declaration.psiAnnotation.qualifiedName ?: return false
    val elementToHighlight = getElementToHighlight(sourceNode) ?: return false
    return usageIgnored(elementToHighlight, annotationFqn)
  }

  private fun checkUnstableApiUsage(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
    if (ignoreApiDeclaredInThisProject && !isLibraryElement(target)) {
      return
    }

    if (checkTargetIsUnstableItself(target, sourceNode, isMethodOverriding)) {
      return
    }

    checkTargetReferencesUnstableTypeInSignature(target, sourceNode, isMethodOverriding)
  }

  private fun checkTargetIsUnstableItself(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean): Boolean {
    val annotatedContainingDeclaration = findAnnotatedContainingDeclaration(target, unstableApiAnnotations, true)
    if (annotatedContainingDeclaration != null) {
      if (isUsageIgnored(annotatedContainingDeclaration, sourceNode)) {
        return true
      }
      val messageProvider = getMessageProvider(annotatedContainingDeclaration.psiAnnotation) ?: return false
      val message = if (isMethodOverriding) {
        messageProvider.buildMessageUnstableMethodOverridden(annotatedContainingDeclaration)
      }
      else {
        messageProvider.buildMessage(annotatedContainingDeclaration)
      }
      val elementToHighlight = getElementToHighlight(sourceNode) ?: return false
      val fix = DeprecationInspection.getReplacementQuickFix(target, elementToHighlight)
      if (fix != null) {
        problemsHolder.registerProblem(elementToHighlight, message, messageProvider.problemHighlightType, fix)
      }
      else {
        problemsHolder.registerProblem(elementToHighlight, message, messageProvider.problemHighlightType)
      }
      return true
    }
    return false
  }

  private fun checkTargetReferencesUnstableTypeInSignature(target: PsiModifierListOwner,
                                                           sourceNode: UElement,
                                                           isMethodOverriding: Boolean) {
    if (!isMethodOverriding && !arePsiElementsFromTheSameFile(sourceNode.sourcePsi, target.containingFile)) {
      val declaration = target.toUElement(UDeclaration::class.java)
      if (declaration !is UClass && declaration !is UMethod && declaration !is UField) {
        return
      }
      val unstableTypeUsedInSignature = findAnnotatedTypeUsedInDeclarationSignature(declaration, unstableApiAnnotations)
      if (unstableTypeUsedInSignature != null) {
        if (isUsageIgnored(unstableTypeUsedInSignature, sourceNode)) {
          return
        }
        val messageProvider = getMessageProvider(unstableTypeUsedInSignature.psiAnnotation) ?: return
        val message = messageProvider.buildMessageUnstableTypeIsUsedInSignatureOfReferencedApi(target, unstableTypeUsedInSignature)
        val elementToHighlight = getElementToHighlight(sourceNode) ?: return
        problemsHolder.registerProblem(elementToHighlight, message, messageProvider.problemHighlightType)
      }
    }
  }

  private fun arePsiElementsFromTheSameFile(one: PsiElement?, two: PsiElement?): Boolean {
    //For Kotlin: naive comparison of PSI containingFile-s does not work because one of the PSI elements might be light PSI element
    // coming from a light PSI file, and another element would be physical PSI file, and they are not "equals()".
    return one?.containingFile?.virtualFile == two?.containingFile?.virtualFile
  }
}
