// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.daemon.QuickFixBundle
import com.intellij.lang.jvm.actions.AnnotationAttributeValueRequest
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager


private val LOG = logger<CreateAnnotationAction>()

internal class CreateAnnotationAction(target: PsiModifierListOwner, override val request: AnnotationRequest) :
  CreateTargetAction<PsiModifierListOwner>(target, request) {

  override fun getText(): String =
    QuickFixBundle.message("create.annotation.text", StringUtilRt.getShortName(request.qualifiedName))

  override fun getFamilyName(): String = QuickFixBundle.message("create.annotation.family")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val modifierList = target.modifierList ?: return
    val annotation = modifierList.addAnnotation(request.qualifiedName)

    val psiElementFactory = PsiElementFactory.SERVICE.getInstance(project)

    attributes@ for ((name, value) in request.attributes) {
      val memberValue = when (value) {
        is AnnotationAttributeValueRequest.PrimitiveValue -> psiElementFactory
          .createExpressionFromText(value.value.toString(), null)
        is AnnotationAttributeValueRequest.StringValue -> psiElementFactory
          .createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value.value) + "\"", null)
        else -> {
          LOG.error("adding annotation members of ${value.javaClass} type is not implemented"); continue@attributes
        }
      }
      annotation.setDeclaredAttributeValue(name.takeIf { name != "value" }, memberValue)
    }

    val formatter = CodeStyleManager.getInstance(project)
    val codeStyleManager = JavaCodeStyleManager.getInstance(project)
    codeStyleManager.shortenClassReferences(formatter.reformat(annotation))

  }

}

