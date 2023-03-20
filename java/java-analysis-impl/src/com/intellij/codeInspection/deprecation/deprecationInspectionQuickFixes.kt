/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.deprecation

import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.codeInspection.LocalQuickFixOnPsiElement
import com.intellij.openapi.project.Project
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

internal class ReplaceMethodCallFix(expr: PsiMethodCallExpression, replacementMethod: PsiMethod) : LocalQuickFixOnPsiElement(expr) {
  @SafeFieldForPreview
  private val myReplacementMethodPointer =
    SmartPointerManager.getInstance(replacementMethod.project).createSmartPsiElementPointer(replacementMethod)
  private val myReplacementText =
    PsiFormatUtil.formatMethod(replacementMethod, PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_CONTAINING_CLASS or PsiFormatUtilBase.SHOW_NAME, 0)

  override fun getText(): String {
    return InspectionGadgetsBundle.message("replace.method.call.fix.text", myReplacementText)
  }

  @Nls
  override fun getFamilyName(): String {
    return InspectionGadgetsBundle.message("replace.method.call.fix.family.name")
  }

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val expr = (startElement as? PsiMethodCallExpression) ?: return
    val replacementMethod = myReplacementMethodPointer.element ?: return

    val qualifierText = generateQualifierText(expr.methodExpression, replacementMethod)

    val elementFactory = JavaPsiFacade.getElementFactory(project)
    val newMethodCall = elementFactory.createExpressionFromText(qualifierText + replacementMethod.name + expr.argumentList.text, expr)
    val replaced = expr.replace(newMethodCall) as PsiMethodCallExpression
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced.methodExpression)
  }
}

internal class ReplaceFieldReferenceFix(expr: PsiReferenceExpression, replacementMember: PsiMember) : LocalQuickFixOnPsiElement(expr) {
  @SafeFieldForPreview
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

  override fun getText(): String {
    return InspectionGadgetsBundle.message("replace.field.reference.fix.text", myReplacementText)
  }

  override fun invoke(project: Project, file: PsiFile, startElement: PsiElement, endElement: PsiElement) {
    val expr = (startElement as? PsiReferenceExpression) ?: return
    val replacementMember = myReplacementMemberPointer.element ?: return

    val qualifierText = generateQualifierText(expr, replacementMember)

    val replaced = expr.replace(JavaPsiFacade.getElementFactory(project).createExpressionFromText(
      qualifierText + replacementMember.name + (if (replacementMember is PsiMethod) "()" else ""), expr))
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(replaced)
  }
}