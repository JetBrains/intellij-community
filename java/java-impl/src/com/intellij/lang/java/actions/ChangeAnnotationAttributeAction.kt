// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.lang.jvm.actions.AnnotationAttributeRequest
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.modcommand.Presentation
import com.intellij.modcommand.PsiUpdateModCommandAction
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElementFactory

internal class ChangeAnnotationAttributeAction(annotation: PsiAnnotation,
                                               val request: AnnotationAttributeRequest,
                                               @IntentionName private val text: String,
                                               @IntentionFamilyName private val familyName: String) :
  PsiUpdateModCommandAction<PsiAnnotation>(annotation) {

  override fun getFamilyName(): String = familyName

  override fun getPresentation(context: ActionContext, element: PsiAnnotation): Presentation {
    return Presentation.of(text)
  }

  override fun invoke(context: ActionContext, annotation: PsiAnnotation, updater: ModPsiUpdater) {
    val factory = PsiElementFactory.getInstance(context.project)
    val value = CreateAnnotationActionUtil.attributeRequestToValue(request.value, factory, null)
    annotation.setDeclaredAttributeValue(request.name, value)
  }
}