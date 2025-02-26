// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.MethodSignatureUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.uast.UastVisitorAdapter
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.USuperExpression
import org.jetbrains.uast.getAnchorPsi
import org.jetbrains.uast.getContainingUMethod

private inline val ANNOTATION_NAME get() = ApiStatus.OverrideOnly::class.java.canonicalName!!

/**
 * UAST-based inspection checking that no API method, which is marked with [ApiStatus.OverrideOnly] annotation,
 * is referenced or invoked in client code.
 */
@VisibleForTesting
class OverrideOnlyInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    if (AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(ANNOTATION_NAME, holder.file)) {
      val apiUsageProcessor = OverrideOnlyProcessor(holder)
      UastVisitorAdapter(OverrideOnlyVisitor(apiUsageProcessor, holder), true)
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  private class OverrideOnlyVisitor(
    apiUsageProcessor: ApiUsageProcessor,
    private val problemsHolder: ProblemsHolder,
  ) : ApiUsageUastVisitor(apiUsageProcessor) {
    override fun visitMethod(method: UMethod): Boolean {
      val containingClass = method.uastParent as? UClass
      val hasAnnotation = method.findAnnotation(ANNOTATION_NAME) != null
      if (hasAnnotation && (containingClass == null || containingClass.isFinal || !method.javaPsi.isOverridable())) {
        val anchor = method.getAnchorPsi() ?: return true
        val methodName = HighlightMessageUtil.getSymbolName(method.javaPsi) ?: return true
        val description = JvmAnalysisBundle.message("jvm.inspections.api.override.only.on.invalid.method.description", methodName)
        problemsHolder.registerProblem(anchor, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }

      return true
    }
  }

  private class OverrideOnlyProcessor(private val problemsHolder: ProblemsHolder) : ApiUsageProcessor {


    private fun isLibraryElement(method: PsiMethod): Boolean {
      val containingVirtualFile = PsiUtilCore.getVirtualFile(method)
      return containingVirtualFile != null && ProjectFileIndex.getInstance(method.project).isInLibraryClasses(containingVirtualFile)
    }

    private fun isOverrideOnlyMethod(method: PsiMethod): Boolean {
      return method.hasAnnotation(ANNOTATION_NAME) || method.containingClass?.hasAnnotation(ANNOTATION_NAME) == true
    }

    private fun isInsideOverridenOnlyMethod(sourceNode: UElement, target: PsiMethod): Boolean = sourceNode.getContainingUMethod()?.let {
      MethodSignatureUtil.areSignaturesEqual(it.javaPsi, target)
    } ?: false

    private fun isSuperCall(qualifier: UExpression?) = qualifier is USuperExpression

    private fun isDelegateCall(qualifier: UExpression?, target: PsiMethod) = qualifier?.let {
      val methodClassName = target.containingClass?.qualifiedName ?: return@let false
      it.getExpressionType()?.isInheritorOf(methodClassName)
    } ?: false

    private fun isSuperOrDelegateCall(sourceNode: UElement, target: PsiMethod, qualifier: UExpression?): Boolean {
      if (!isInsideOverridenOnlyMethod(sourceNode, target)) return false

      return isSuperCall(qualifier) || isDelegateCall(qualifier, target)
    }

    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      if (target is PsiMethod && target.isOverridable() && isLibraryElement(target) && isOverrideOnlyMethod(target)
          && !isSuperOrDelegateCall(sourceNode, target, qualifier)) {
        val elementToHighlight = sourceNode.sourcePsi ?: return
        val methodName = HighlightMessageUtil.getSymbolName(target) ?: return
        val description = JvmAnalysisBundle.message("jvm.inspections.api.override.only.description", methodName)
        problemsHolder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}

private fun PsiMethod.isOverridable(): Boolean {
  return !hasModifier(JvmModifier.PRIVATE) && !hasModifier(JvmModifier.FINAL) && !hasModifier(JvmModifier.STATIC)
}
