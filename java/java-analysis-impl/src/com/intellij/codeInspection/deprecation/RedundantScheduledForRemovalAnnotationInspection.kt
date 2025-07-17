// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.RemoveAnnotationQuickFix
import com.intellij.java.analysis.JavaAnalysisBundle
import com.intellij.java.syntax.parser.JavaKeywords
import com.intellij.lang.jvm.annotation.JvmAnnotationConstantValue
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.PsiUpdateModCommandQuickFix
import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiUtil
import org.jetbrains.annotations.ApiStatus

public class RedundantScheduledForRemovalAnnotationInspection : LocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    if (!PsiUtil.isLanguageLevel9OrHigher(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
      
    return object : JavaElementVisitor() {
      override fun visitClass(aClass: PsiClass) {
        visitAnnotatedElement(aClass)
      }

      override fun visitMethod(method: PsiMethod) {
        visitAnnotatedElement(method)
      }

      override fun visitField(field: PsiField) {
        visitAnnotatedElement(field)
      }

      private fun visitAnnotatedElement(element: PsiModifierListOwner) {
        val forRemovalAnnotation = element.getAnnotation(ApiStatus.ScheduledForRemoval::class.java.canonicalName) ?: return
        if (forRemovalAnnotation.hasAttribute("inVersion")) return
        val deprecatedAnnotation = element.getAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED) ?: return
        val forRemovalAttribute = deprecatedAnnotation.findAttribute("forRemoval")?.attributeValue
        val alreadyHasAttribute = (forRemovalAttribute as? JvmAnnotationConstantValue)?.constantValue == true
        if (alreadyHasAttribute) {
          holder.registerProblem(forRemovalAnnotation, JavaAnalysisBundle.message("inspection.message.scheduled.for.removal.annotation.can.be.removed"), RemoveAnnotationQuickFix(forRemovalAnnotation, element))
        }
        else {
          val fix = ReplaceAnnotationByForRemovalAttributeFix()
          holder.registerProblem(forRemovalAnnotation, JavaAnalysisBundle.message("inspection.message.scheduled.for.removal.annotation.can.be.replaced.by.attribute"), fix)
        }
      }
    }
  }

  public class ReplaceAnnotationByForRemovalAttributeFix : PsiUpdateModCommandQuickFix() {
    override fun applyFix(project: Project, element: PsiElement, updater: ModPsiUpdater) {
      val forRemovalAnnotation = element as? PsiAnnotation ?: return
      val deprecatedAnnotation = forRemovalAnnotation.owner?.findAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED) ?: return
      val javaFile = forRemovalAnnotation.containingFile as? PsiJavaFile
      forRemovalAnnotation.delete()
      val trueLiteral = JavaPsiFacade.getElementFactory(project).createExpressionFromText(JavaKeywords.TRUE, null)
      deprecatedAnnotation.setDeclaredAttributeValue("forRemoval", trueLiteral)
      if (javaFile != null) {
        JavaCodeStyleManager.getInstance(project).removeRedundantImports(javaFile)
      }
    }

    override fun getName(): String = familyName

    override fun getFamilyName(): String {
      return JavaAnalysisBundle.message("inspection.fix.name.remove.scheduled.for.removal.annotation.by.attribute")
    }
  }
}