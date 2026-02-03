// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.codeInsight.intention.FileModifier.SafeFieldForPreview
import com.intellij.lang.jvm.actions.ChangeTypeRequest
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiTypeElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager
import com.siyeh.ig.psiutils.CommentTracker

internal class ChangeType(
  typeElement: PsiTypeElement,
  @SafeFieldForPreview override val request: ChangeTypeRequest
  ) : CreateTargetAction<PsiTypeElement>(typeElement, request) {
  
  override fun getText(): String {
    val typeName = request.qualifiedName ?: return familyName
    return QuickFixBundle.message("change.type.text", typeName)
  }

  override fun getFamilyName(): String = QuickFixBundle.message("change.type.family")

  override fun invoke(project: Project, file: PsiFile, target: PsiTypeElement) {
    val factory = PsiElementFactory.getInstance(project)
    val typeName = request.qualifiedName ?: target.type.canonicalText
    val newType = factory.createTypeFromText(typeName, target)
    var typeElement = factory.createTypeElement(newType)
    typeElement = CommentTracker().replace(target, typeElement) as PsiTypeElement
    request.annotations.forEach { CreateAnnotationAction.addAnnotationToAnnotationOwner(typeElement, typeElement, it) }
    val formatter = CodeStyleManager.getInstance(project)
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    codeStyleManager.shortenClassReferences(formatter.reformat(typeElement))
  }
}