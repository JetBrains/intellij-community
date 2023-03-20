// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.intention.AddAnnotationFix
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiModifierListOwner
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

/**
 * Reports declarations (classes, methods, fields) marked with [ApiStatus.ScheduledForRemoval] annotation
 * that are not accompanied by [Deprecated] annotation.
 */
class MissingDeprecatedAnnotationOnScheduledForRemovalApiInspection : LocalInspectionTool() {

  private companion object {
    private val DEPRECATED_ANNOTATION_NAME = java.lang.Deprecated::class.java.canonicalName
    private val KOTLIN_DEPRECATED_ANNOTATION_NAME = Deprecated::class.java.canonicalName
    private val SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME = ApiStatus.ScheduledForRemoval::class.java.canonicalName
  }

  override fun runForWholeFile() = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME, holder.file)) {
      return PsiElementVisitor.EMPTY_VISITOR
    }
    return UastVisitorAdapter(MissingDeprecatedAnnotationOnSFRVisitor(holder), true)
  }

  private class MissingDeprecatedAnnotationOnSFRVisitor(private val problemsHolder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitClass(node: UClass): Boolean {
      checkMissingDeprecatedAnnotationOnScheduledForRemovalApi(node)
      return true
    }

    override fun visitMethod(node: UMethod): Boolean {
      checkMissingDeprecatedAnnotationOnScheduledForRemovalApi(node)
      return true
    }

    override fun visitField(node: UField): Boolean {
      checkMissingDeprecatedAnnotationOnScheduledForRemovalApi(node)
      return true
    }

    private fun checkMissingDeprecatedAnnotationOnScheduledForRemovalApi(node: UDeclaration) {
      if (isScheduledForRemoval(node) && !hasDeprecatedAnnotation(node)) {
        val identifierPsi = node.uastAnchor.sourcePsiElement ?: return
        problemsHolder.registerProblem(
          identifierPsi,
          JvmAnalysisBundle.message("jvm.inspections.missing.deprecated.annotation.on.scheduled.for.removal.api.description"),
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
          *LocalQuickFix.notNullElements(createAnnotationFix(node))
        )
      }
    }

    private fun createAnnotationFix(node: UDeclaration): AddAnnotationFix? {
      //This quick fix works only for Java.
      val modifierListOwner = node.sourcePsi as? PsiModifierListOwner ?: return null
      return AddAnnotationFix(DEPRECATED_ANNOTATION_NAME, modifierListOwner)
    }

    private fun hasDeprecatedAnnotation(node: UAnnotated) =
      node.findAnnotation(DEPRECATED_ANNOTATION_NAME) != null || node.findAnnotation(KOTLIN_DEPRECATED_ANNOTATION_NAME) != null

    private fun isScheduledForRemoval(annotated: UAnnotated): Boolean =
      annotated.findAnnotation(SCHEDULED_FOR_REMOVAL_ANNOTATION_NAME) != null
  }

}

