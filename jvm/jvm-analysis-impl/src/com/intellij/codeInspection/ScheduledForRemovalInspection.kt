// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.UnstableApiUsageInspection.Companion.findAnnotationOfItselfOrContainingDeclaration
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

class ScheduledForRemovalInspection : LocalInspectionTool() {

  @JvmField
  var myIgnoreInsideImports: Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    ApiUsageUastVisitor.createPsiElementVisitor(
      ScheduledForRemovalApiUsageProcessor(holder, myIgnoreInsideImports)
    )

  override fun createOptionsPanel() = SingleCheckboxOptionsPanel(
    JvmAnalysisBundle.message("jvm.inspections.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports"
  )
}

private class ScheduledForRemovalApiUsageProcessor(
  private val problemsHolder: ProblemsHolder,
  private val ignoreInsideImports: Boolean
) : ApiUsageProcessor {

  private companion object {
    private val ANNOTATION_NAME = ApiStatus.ScheduledForRemoval::class.java.canonicalName

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
      checkScheduledForRemovalApiUsage(target, sourceNode, false)
    }
  }

  override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
    checkScheduledForRemovalApiUsage(target, sourceNode, false)
  }

  override fun processConstructorInvocation(sourceNode: UElement,
                                            instantiatedClass: PsiClass,
                                            constructor: PsiMethod?,
                                            subclassDeclaration: UClass?) {
    if (constructor != null) {
      checkScheduledForRemovalApiUsage(constructor, sourceNode, false)
    }
  }

  override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
    checkScheduledForRemovalApiUsage(overriddenMethod, method, true)
  }

  fun checkScheduledForRemovalApiUsage(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
    if (!isLibraryElement(target)) {
      return
    }
    val scheduledForRemovalAnnotation = findAnnotationOfItselfOrContainingDeclaration(target, listOf(ANNOTATION_NAME), false)
    if (scheduledForRemovalAnnotation == null) {
      return
    }
    val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi
    if (elementToHighlight != null) {
      val message = buildMessage(scheduledForRemovalAnnotation, target, isMethodOverriding)
      problemsHolder.registerProblem(elementToHighlight, message, ProblemHighlightType.LIKE_MARKED_FOR_REMOVAL)
    }
  }

  private fun buildMessage(
    scheduledForRemovalAnnotation: PsiAnnotation,
    target: PsiModifierListOwner,
    isMethodOverriding: Boolean
  ): String {
    val inVersion = AnnotationUtil.getDeclaredStringAttributeValue(scheduledForRemovalAnnotation, "inVersion")
    val targetName = DeprecationInspectionBase.getPresentableName(target)
    val isEmptyVersion = inVersion == null || inVersion.isEmpty()
    return when {
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
  }
}