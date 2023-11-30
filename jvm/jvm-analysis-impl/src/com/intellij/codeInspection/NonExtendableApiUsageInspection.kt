// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtilCore
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.uast.*

/**
 * UAST-based inspection checking that no API class, interface or method, which is marked with [ApiStatus.NonExtendable] annotations,
 * is extended, implemented or overridden in client code.
 */
class NonExtendableApiUsageInspection : LocalInspectionTool() {

  private val ANNOTATION_NAME: String = ApiStatus.NonExtendable::class.java.canonicalName!!

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    if (AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(ANNOTATION_NAME, holder.file)) {
      ApiUsageUastVisitor.createPsiElementVisitor(NonExtendableApiUsageProcessor(holder))
    } else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  private inner class NonExtendableApiUsageProcessor(private val problemsHolder: ProblemsHolder) : ApiUsageProcessor {

    private fun isLibraryElement(element: PsiElement): Boolean {
      val virtualFile = PsiUtilCore.getVirtualFile(element)
      return virtualFile != null && ProjectFileIndex.getInstance(element.project).isInLibraryClasses(virtualFile)
    }

    private fun isSuperClassReferenceInSubclassDeclaration(subclassDeclaration: UClass, superClass: PsiClass) =
      subclassDeclaration.uastSuperTypes.any { superClass.manager.areElementsEquivalent(superClass, PsiTypesUtil.getPsiClass(it.type)) }

    override fun processReference(sourceNode: UElement, target: PsiModifierListOwner, qualifier: UExpression?) {
      if (target !is PsiClass || !target.hasAnnotation(ANNOTATION_NAME)) {
        return
      }
      val classDeclaration = sourceNode.sourcePsi.findContaining(UClass::class.java)
      if (classDeclaration == null || !isSuperClassReferenceInSubclassDeclaration(classDeclaration, target)) {
        return
      }

      val elementToHighlight = sourceNode.sourcePsi ?: return
      if (isLibraryElement(target)) {
        val className = HighlightMessageUtil.getSymbolName(target) ?: return
        val description = if (target.isInterface) {
          if (classDeclaration.isInterface) {
            JvmAnalysisBundle.message("jvm.inspections.api.no.extension.interface.extend.description", className)
          }
          else {
            JvmAnalysisBundle.message("jvm.inspections.api.no.extension.interface.implement.description", className)
          }
        }
        else {
          JvmAnalysisBundle.message("jvm.inspections.api.no.extension.class.description", className)
        }
        problemsHolder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }

    override fun processLambda(sourceNode: ULambdaExpression, target: PsiModifierListOwner) {
      processReference(sourceNode, target, null)
    }

    override fun processMethodOverriding(method: UMethod, overriddenMethod: PsiMethod) {
      val elementToHighlight = method.uastAnchor.sourcePsiElement ?: return
      if (overriddenMethod.hasAnnotation(ANNOTATION_NAME) && isLibraryElement(overriddenMethod)) {
        val methodName = HighlightMessageUtil.getSymbolName(overriddenMethod) ?: return
        val description = JvmAnalysisBundle.message("jvm.inspections.api.no.extension.method.overriding.description", methodName)
        problemsHolder.registerProblem(elementToHighlight, description, ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      }
    }
  }
}
