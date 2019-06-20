// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.deprecation.DeprecationInspectionBase.getPresentableName
import com.intellij.codeInspection.util.SpecialAnnotationsUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.siyeh.ig.ui.ExternalizableStringSet
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.sourcePsiElement
import java.awt.BorderLayout
import javax.swing.JPanel

class UnstableApiUsageInspection : AnnotatedElementInspectionBase() {

  @JvmField
  val unstableApiAnnotations: MutableList<String> = ExternalizableStringSet(
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

  override fun getAnnotations() = unstableApiAnnotations

  override fun buildAnnotatedApiUsageProcessor(holder: ProblemsHolder) =
    object : AnnotatedApiUsageProcessor {
      override fun processAnnotatedTarget(
        sourceNode: UElement,
        annotatedTarget: PsiModifierListOwner,
        annotations: List<PsiAnnotation>
      ) {
        checkUnstableApiUsage(annotatedTarget, sourceNode, false)
      }

      override fun processAnnotatedMethodOverriding(
        method: UMethod,
        overriddenMethod: PsiMethod,
        annotations: List<PsiAnnotation>
      ) {
        checkUnstableApiUsage(overriddenMethod, method, true)
      }

      private fun checkUnstableApiUsage(annotatedTarget: PsiModifierListOwner, sourceNode: UElement, isMethodOverriding: Boolean) {
        if (!isLibraryElement(annotatedTarget)) {
          return
        }
        val targetName = getPresentableName(annotatedTarget)
        val message = if (isMethodOverriding) {
          JvmAnalysisBundle.message("jvm.inspections.unstable.method.overridden.description", targetName)
        } else {
          JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.description", targetName)
        }
        val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi
        if (elementToHighlight != null) {
          holder.registerProblem(elementToHighlight, message, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
        }
      }
    }

  override fun createOptionsPanel(): JPanel {
    val checkboxPanel = super.createOptionsPanel()

    //TODO in add annotation window "Include non-project items" should be enabled by default
    val annotationsListControl = SpecialAnnotationsUtil.createSpecialAnnotationsListControl(
      unstableApiAnnotations, JvmAnalysisBundle.message("jvm.inspections.unstable.api.usage.annotations.list"))

    val panel = JPanel(BorderLayout(2, 2))
    panel.add(checkboxPanel, BorderLayout.NORTH)
    panel.add(annotationsListControl, BorderLayout.CENTER)
    return panel
  }
}
