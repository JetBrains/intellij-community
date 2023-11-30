// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.psi.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.uast.*

private inline val OBSOLETE_ANNOTATION_NAME get() = ApiStatus.Obsolete::class.java.canonicalName

@VisibleForTesting
class ObsoleteApiUsageInspection : LocalInspectionTool() {

  private fun shouldInspect(file: PsiFile) = JavaPsiFacade.getInstance(file.project)
    .findClass(OBSOLETE_ANNOTATION_NAME, file.resolveScope) != null

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!shouldInspect(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
    return ApiUsageUastVisitor.createPsiElementVisitor(ObsoleteApiUsageProcessor(holder))
  }

  private class ObsoleteApiUsageProcessor(private val problemsHolder: ProblemsHolder) : ApiUsageProcessor {
    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      checkObsoleteApiUsage(target, sourceNode)
    }

    override fun processConstructorInvocation(
      sourceNode: UElement,
      instantiatedClass: PsiClass,
      constructor: PsiMethod?,
      subclassDeclaration: UClass?
    ) {
      if (constructor != null) checkObsoleteApiUsage(constructor, sourceNode)
    }

    override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
      checkObsoleteApiUsage(overriddenMethod, method)
    }

    private fun checkObsoleteApiUsage(target: PsiModifierListOwner, sourceNode: UElement) {
      val declaration = target.toUElement(UDeclaration::class.java)
      if (declaration != null && !arePsiElementsFromTheSameFile(sourceNode.sourcePsi, target)) {
        if (declaration !is UClass && declaration !is UMethod && declaration !is UField) return
        if (declaration.findAnnotation(OBSOLETE_ANNOTATION_NAME) != null) {
          val elementToHighlight = (sourceNode as? UDeclaration)?.uastAnchor.sourcePsiElement ?: sourceNode.sourcePsi ?: return
          // Do not highlight method references that map to obsolete functional interface.
          // this problem will be highlighted elsewhere (e.g., at declaration of the method accepting functional interface) 
          if (elementToHighlight is PsiMethodReferenceExpression && target is PsiClass) return
          if (elementToHighlight is PsiReference) {
            problemsHolder.registerProblem(
              elementToHighlight as PsiReference, JvmAnalysisBundle.message("jvm.inspections.usages.of.obsolete.api.description"),
              ProblemHighlightType.GENERIC_ERROR_OR_WARNING
            )
          }
          else {
            problemsHolder.registerProblem(
              elementToHighlight, JvmAnalysisBundle.message("jvm.inspections.usages.of.obsolete.api.description")
            )
          }
        }
      }
    }

    private fun arePsiElementsFromTheSameFile(one: PsiElement?, two: PsiElement?): Boolean {
      // For Kotlin: naive comparison of PSI containingFile-s does not work because one of the PSI elements might be light PSI element
      // coming from a light PSI file, and another element would be physical PSI file, and they are not "equals()".
      return one?.containingFile?.virtualFile == two?.containingFile?.virtualFile
    }
  }
}