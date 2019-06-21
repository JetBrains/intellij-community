// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase.getPresentableName
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ArrayUtilRt
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.uast.*
import java.awt.BorderLayout
import javax.swing.JPanel

class UnstableApiUsageInspection : LocalInspectionTool() {

  companion object {
    val DEFAULT_UNSTABLE_API_ANNOTATIONS: List<String> = listOf(
      "org.jetbrains.annotations.ApiStatus.Experimental",
      "org.jetbrains.annotations.ApiStatus.Internal",
      "com.google.common.annotations.Beta",
      "io.reactivex.annotations.Beta",
      "io.reactivex.annotations.Experimental",
      "rx.annotations.Experimental",
      "rx.annotations.Beta",
      "org.apache.http.annotation.Beta",
      "org.gradle.api.Incubating"
    )
  }

  @JvmField
  val unstableApiAnnotations: List<String> = ExternalizableStringSet(
    *ArrayUtilRt.toStringArray(DEFAULT_UNSTABLE_API_ANNOTATIONS)
  )

  @JvmField
  var myIgnoreInsideImports: Boolean = true

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean) =
    ApiUsageUastVisitor.createPsiElementVisitor(
      UnstableApiUsageProcessor(holder, myIgnoreInsideImports, unstableApiAnnotations.toList())
    )

  override fun createOptionsPanel(): JPanel {
    val checkboxPanel = SingleCheckboxOptionsPanel(
      JvmAnalysisBundle.message("jvm.inspections.api.usage.ignore.inside.imports"), this, "myIgnoreInsideImports"
    )

    //TODO in add annotation window "Include non-project items" should be enabled by default
    val annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list")
    )

    val panel = JPanel(BorderLayout(2, 2))
    panel.add(checkboxPanel, BorderLayout.NORTH)
    panel.add(annotationsListControl, BorderLayout.CENTER)
    return panel
  }
}

private class UnstableApiUsageProcessor(
  private val problemsHolder: ProblemsHolder,
  private val ignoreInsideImports: Boolean,
  private val annotations: List<String>
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

  private fun checkUnstableApiUsage(target: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
    if (!isLibraryElement(target)) {
      return
    }
    val annotations = AnnotationUtil.findAllAnnotations(target, annotations, false)
    if (annotations.isEmpty()) {
      return
    }
    val targetName = getPresentableName(target)
    val message = if (isMethodOverriding) {
      JvmAnalysisBundle.message("jvm.inspections.unstable.method.overridden.description", targetName)
    }
    else {
      JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.description", targetName)
    }
    val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi
    if (elementToHighlight != null) {
      problemsHolder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
    }
  }

}
