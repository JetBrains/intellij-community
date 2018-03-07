// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.FileModificationService
import com.intellij.lang.jvm.actions.CreateAnnotationRequest
import com.intellij.lang.jvm.actions.JvmAnnotationMemberValue
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.psi.LanguageAnnotationSupport
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager


private val LOG = logger<CreateAnnotationAction>()

class CreateAnnotationAction(target: PsiModifierListOwner, override val request: CreateAnnotationRequest) :
  CreateTargetAction<PsiModifierListOwner>(target, request) {

  override fun getText(): String = "Add @" + StringUtilRt.getShortName(request.qualifiedName); // TODO: i11n

  override fun getFamilyName(): String = "create annotation family " // TODO: i11n

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {

    val modifierList = target.modifierList ?: return

    if (!FileModificationService.getInstance().preparePsiElementForWrite(modifierList)) return

    WriteCommandAction.writeCommandAction(modifierList.project).run<RuntimeException> {
      val annotation = modifierList.addAnnotation(request.qualifiedName)

      val support = LanguageAnnotationSupport.INSTANCE.forLanguage(annotation.language)
      val psiLiteral = annotation.setDeclaredAttributeValue("value", support!!
        .createLiteralValue(request.valueLiteralValue, annotation))

      val formatter = CodeStyleManager.getInstance(project)
      val codeStyleManager = JavaCodeStyleManager.getInstance(project)
      codeStyleManager.shortenClassReferences(formatter.reformat(annotation))

    }


  }

}

