// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.daemon.impl.analysis.HighlightMessageUtil
import com.intellij.codeInspection.apiUsage.ApiUsageProcessor
import com.intellij.codeInspection.apiUsage.ApiUsageUastVisitor
import com.intellij.lang.jvm.JvmModifier
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.ElementDescriptionUtil
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.intellij.psi.util.PsiTypesUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.uast.UastVisitorAdapter
import com.intellij.usageView.UsageViewTypeLocation
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.uast.UClass
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UExpression
import org.jetbrains.uast.ULambdaExpression
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.findContaining
import org.jetbrains.uast.getContainingUClass
import org.jetbrains.uast.sourcePsiElement

private inline val ANNOTATION_NAME get() = ApiStatus.NonExtendable::class.java.canonicalName!!

/**
 * UAST-based inspection checking that no API class, interface or method, which is marked with [ApiStatus.NonExtendable] annotations,
 * is extended, implemented or overridden in client code.
 *
 * It also checks that the annotation itself is applied on a correct target.
 *
 * @see OverrideOnlyApiInspection
 */
@VisibleForTesting
class NonExtendableApiInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
    if (AnnotatedApiUsageUtil.canAnnotationBeUsedInFile(ANNOTATION_NAME, holder.file)) {
      val apiUsageProcessor = NonExtendableApiUsageProcessor(holder)
      UastVisitorAdapter(NonExtendableApiVisitor(apiUsageProcessor, holder), true)
    }
    else {
      PsiElementVisitor.EMPTY_VISITOR
    }

  private class NonExtendableApiVisitor(
    apiUsageProcessor: ApiUsageProcessor,
    private val problemsHolder: ProblemsHolder,
  ) : ApiUsageUastVisitor(apiUsageProcessor) {

    override fun visitClass(node: UClass): Boolean {
      val hasAnnotation = node.findAnnotation(ANNOTATION_NAME) != null
      if (hasAnnotation && node.isFinal) {
        val options = PsiFormatUtilBase.SHOW_NAME
        val className = PsiFormatUtil.formatClass(node.javaPsi, options)
        val elementName = StringUtil.capitalize(ElementDescriptionUtil.getElementDescription(node.javaPsi, UsageViewTypeLocation.INSTANCE))
        val description = JvmAnalysisBundle.message("jvm.inspections.api.no.extension.on.invalid.target.class.description", elementName, className)
        problemsHolder.registerUProblem(node, description)
      }
      return super.visitClass(node)
    }

    override fun visitMethod(node: UMethod): Boolean {
      val containingClass = node.getContainingUClass()
      val hasAnnotation = node.findAnnotation(ANNOTATION_NAME) != null
      if (hasAnnotation) {
        val isRedundant = containingClass?.isFinal == false && containingClass.findAnnotation(ANNOTATION_NAME) != null
        val isIncorrect = (containingClass == null || containingClass.isFinal || !node.javaPsi.isOverridable())
        val methodName = HighlightMessageUtil.getSymbolName(node.javaPsi) ?: return super.visitMethod(node)

        val description = if (isRedundant) JvmAnalysisBundle.message("jvm.inspections.api.no.extension.on.redundant.target.method.description")
        else if (isIncorrect) JvmAnalysisBundle.message("jvm.inspections.api.no.extension.on.invalid.target.method.description", methodName)
        else null

        if (description != null) {
          problemsHolder.registerUProblem(node, description)
        }
      }

      return super.visitMethod(node)
    }
  }

  private class NonExtendableApiUsageProcessor(private val problemsHolder: ProblemsHolder) : ApiUsageProcessor {

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

private fun PsiMethod.isOverridable(): Boolean {
  return !hasModifier(JvmModifier.PRIVATE) && !hasModifier(JvmModifier.FINAL) && !hasModifier(JvmModifier.STATIC)
}
