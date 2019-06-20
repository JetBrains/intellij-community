// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.sourcePsiElement

//TODO quickfix like in deprecation inspection?
class ScheduledForRemovalInspection : AnnotatedElementInspectionBase() {

  private companion object {
    private val ANNOTATION_NAME = ApiStatus.ScheduledForRemoval::class.java.canonicalName
  }

  override fun getAnnotations() = listOf(ANNOTATION_NAME)

  override fun buildAnnotatedApiUsageProcessor(holder: ProblemsHolder): AnnotatedApiUsageProcessor =
    object : AnnotatedApiUsageProcessor {
      override fun processAnnotatedTarget(
        sourceNode: UElement,
        annotatedTarget: PsiModifierListOwner,
        annotations: List<PsiAnnotation>
      ) {
        checkScheduledForRemovalApiUsage(annotatedTarget, sourceNode, annotations, false)
      }

      override fun processAnnotatedMethodOverriding(
        method: UMethod,
        overriddenMethod: PsiMethod,
        annotations: List<PsiAnnotation>
      ) {
        checkScheduledForRemovalApiUsage(overriddenMethod, method, annotations, true)
      }

      private fun checkScheduledForRemovalApiUsage(
        annotatedTarget: PsiModifierListOwner,
        sourceNode: UElement,
        annotations: List<PsiAnnotation>,
        isMethodOverriding: Boolean
      ) {
        if (!isLibraryElement(annotatedTarget)) {
          return
        }
        val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi
        val scheduledForRemoval = annotations.find { psiAnnotation -> psiAnnotation.hasQualifiedName(ANNOTATION_NAME) }
        if (elementToHighlight != null && scheduledForRemoval != null) {
          val inVersion = AnnotationUtil.getDeclaredStringAttributeValue(scheduledForRemoval, "inVersion")
          val targetName = getPresentableText(annotatedTarget)
          val isEmptyVersion = inVersion == null || inVersion.isEmpty()
          val message: String = when {
            isEmptyVersion && isMethodOverriding -> JvmAnalysisBundle.message(
              "jvm.inspections.scheduled.for.removal.method.overridden.no.version.description", targetName
            )

            !isEmptyVersion && isMethodOverriding -> JvmAnalysisBundle.message(
              "jvm.inspections.scheduled.for.removal.method.overridden.with.version.description", targetName, inVersion
            )

            !isEmptyVersion && !isMethodOverriding -> JvmAnalysisBundle.message(
              "jvm.inspections.scheduled.for.removal.description.with.version", targetName, inVersion
            )

            else -> JvmAnalysisBundle.message("jvm.inspections.scheduled.for.removal.description.no.version", targetName)
          }
          holder.registerProblem(elementToHighlight, message, ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL)
        }
      }
    }
}
