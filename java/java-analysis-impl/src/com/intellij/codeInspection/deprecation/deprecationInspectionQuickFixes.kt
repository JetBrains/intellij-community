// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.deprecation

import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.*
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.intellij.psi.util.PsiFormatUtil
import com.intellij.psi.util.PsiFormatUtilBase
import com.siyeh.InspectionGadgetsBundle
import org.jetbrains.annotations.Nls

private fun generateQualifierText(expr: PsiReferenceExpression,
                                  replacementMethod: PsiMember): String {
  val qualifierExpression = expr.qualifierExpression
  val isReplacementStatic = replacementMethod.hasModifierProperty(PsiModifier.STATIC)
  return if (qualifierExpression != null && !isReplacementStatic) {
    qualifierExpression.text + "."
  }
  else if (isReplacementStatic) {
    replacementMethod.containingClass!!.qualifiedName!! + "."
  }
  else {
    ""
  }
}

internal class ReplaceMethodCallFix(expr: PsiMethodCallExpression, replacementMethod: PsiMethod) :
  PsiUpdateModCommandAction<PsiMethodCallExpression>(expr) {
  private val myReplacementMethodPointer =
    SmartPointerManager.getInstance(replacementMethod.project).createSmartPsiElementPointer(replacementMethod)
  private val myReplacementText =
    PsiFormatUtil.formatMethod(replacementMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME, 0)

  override fun getPresentation(context: ActionContext, element: PsiMethodCallExpression): Presentation {
    return Presentation.of(InspectionGadgetsBundle.message("replace.method.call.fix.text", myReplacementText))
  }

  @Nls
  override fun getFamilyName(): String {
    return InspectionGadgetsBundle.message("replace.method.call.fix.family.name")
  }

  override fun invoke(context: ActionContext, expr: PsiMethodCallExpression, updater: ModPsiUpdater) {
    val replacementMethod = myReplacementMethodPointer.element ?: return

    val qualifierText = generateQualifierText(expr.methodExpression, replacementMethod)

    val project = context.project
    val elementFactory = JavaPsiFacade.getElementFactory(project)
    val newMethodCall = elementFactory.createExpressionFromText(qualifierText + replacementMethod.name + expr.argumentList.text, expr)
    val replaced = expr.replace(newMethodCall) as PsiMethodCallExpression
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced.methodExpression)
  }
}

internal class ReplaceFieldReferenceFix(expr: PsiReferenceExpression, replacementMember: PsiMember) :
  PsiUpdateModCommandAction<PsiReferenceExpression>(expr) {
  private val myReplacementMemberPointer =
    SmartPointerManager.getInstance(replacementMember.project).createSmartPsiElementPointer(replacementMember)
  private val myReplacementText =
    when (replacementMember) {
      is PsiField -> PsiFormatUtil.formatVariable(
        replacementMember, PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME, PsiSubstitutor.EMPTY)
      is PsiMethod -> PsiFormatUtil.formatMethod(
        replacementMember, PsiSubstitutor.EMPTY, 
        PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME or PsiFormatUtilBase.SHOW_PARAMETERS, 0)
      else -> throw IllegalArgumentException()
    }

  override fun getFamilyName(): String {
    return InspectionGadgetsBundle.message("replace.field.reference.fix.family.name")
  }

  override fun getPresentation(context: ActionContext, element: PsiReferenceExpression): Presentation {
    return Presentation.of(InspectionGadgetsBundle.message("replace.field.reference.fix.text", myReplacementText))
  }

  override fun invoke(context: ActionContext, expr: PsiReferenceExpression, updater: ModPsiUpdater) {
    val replacementMember = myReplacementMemberPointer.element ?: return

    val qualifierText = generateQualifierText(expr, replacementMember)

    val project = context.project
    val replaced = expr.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
      qualifierText + replacementMember.name + (if (replacementMember is PsiMethod) "()" else ""), expr))
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
  }
}