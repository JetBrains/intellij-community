// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.AddAnnotationPsiFix
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager


internal class CreateAnnotationAction(target: PsiModifierListOwner, val request: AnnotationRequest) :
  PsiUpdateModCommandAction<PsiModifierListOwner>(target) {

  override fun getPresentation(context: ActionContext, target: PsiModifierListOwner): Presentation {
    return Presentation.of(AddAnnotationPsiFix.calcText(target, StringUtilRt.getShortName(request.qualifiedName)))
  }

  override fun getFamilyName(): String = QuickFixBundle.message("create.annotation.family")

  override fun invoke(context: ActionContext, target: PsiModifierListOwner, updater: ModPsiUpdater) {
    val modifierList = target.modifierList ?: return
    addAnnotationToModifierList(modifierList, request)
  }

  companion object {
    internal fun addAnnotationToModifierList(modifierList: PsiModifierList, annotationRequest: AnnotationRequest) {
      val list = AddAnnotationPsiFix.expandParameterIfNecessary(modifierList)
      addAnnotationToAnnotationOwner(modifierList, list, annotationRequest)
    }

    internal fun addAnnotationToAnnotationOwner(context: PsiElement,
                                                list: PsiAnnotationOwner,
                                                annotationRequest: AnnotationRequest) {
      val project = context.project
      val annotation = list.findAnnotation(annotationRequest.qualifiedName) ?: list.addAnnotation(annotationRequest.qualifiedName)
      val psiElementFactory = PsiElementFactory.getInstance(project)

      CreateAnnotationActionUtil.fillAnnotationAttributes(annotation, annotationRequest, psiElementFactory, context)

      val formatter = CodeStyleManager.getInstance(project)
      val codeStyleManager = JavaCodeStyleManager.getInstance(project)
      codeStyleManager.shortenClassReferences(formatter.reformat(annotation))
    }
  }
}
