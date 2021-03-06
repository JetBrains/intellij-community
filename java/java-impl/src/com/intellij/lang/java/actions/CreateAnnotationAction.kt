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
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.codeStyle.JavaCodeStyleManager


internal class CreateAnnotationAction(target: PsiModifierListOwner, override val request: AnnotationRequest) :
  CreateTargetAction<PsiModifierListOwner>(target, request) {

  override fun getText(): String =
    QuickFixBundle.message("create.annotation.text", StringUtilRt.getShortName(request.qualifiedName))

  override fun getFamilyName(): String = QuickFixBundle.message("create.annotation.family")

  override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
    val modifierList = target.modifierList ?: return
    addAnnotationToModifierList(modifierList, request)
  }

  companion object {
    private val LOG = logger<CreateAnnotationAction>()
    internal fun addAnnotationToModifierList(modifierList: PsiModifierList, annotationRequest: AnnotationRequest) {
      val project = modifierList.project
      val annotation = modifierList.addAnnotation(annotationRequest.qualifiedName)
      val psiElementFactory = PsiElementFactory.getInstance(project)

      fillAnnotationAttributes(annotation, annotationRequest, psiElementFactory, modifierList)

      val formatter = CodeStyleManager.getInstance(project)
      val codeStyleManager = JavaCodeStyleManager.getInstance(project)
      codeStyleManager.shortenClassReferences(formatter.reformat(annotation))
    }

    private fun fillAnnotationAttributes(annotation: PsiAnnotation,
                                         annotationRequest: AnnotationRequest,
                                         psiElementFactory: PsiElementFactory,
                                         context: PsiElement?) {
      for ((name, value) in annotationRequest.attributes) {
        val memberValue = attributeRequestToValue(value, psiElementFactory, context, annotationRequest)
        annotation.setDeclaredAttributeValue(name.takeIf { name != "value" }, memberValue)
      }
    }

    private fun attributeRequestToValue(value: AnnotationAttributeValueRequest,
                                        psiElementFactory: PsiElementFactory,
                                        context: PsiElement?,
                                        annotationRequest: AnnotationRequest): PsiAnnotationMemberValue? = when (value) {
      is AnnotationAttributeValueRequest.PrimitiveValue -> psiElementFactory
        .createExpressionFromText(value.value.toString(), null)
      is AnnotationAttributeValueRequest.StringValue -> psiElementFactory
        .createExpressionFromText("\"" + StringUtil.escapeStringCharacters(value.value) + "\"", null)
      is AnnotationAttributeValueRequest.ClassValue -> psiElementFactory
        .createExpressionFromText(value.classFqn + ".class", context)
      is AnnotationAttributeValueRequest.ConstantValue -> psiElementFactory
        .createExpressionFromText(value.text, context)
      is AnnotationAttributeValueRequest.NestedAnnotation -> psiElementFactory
        .createAnnotationFromText("@" + value.annotationRequest.qualifiedName, context).also { nested ->
          fillAnnotationAttributes(nested, value.annotationRequest, psiElementFactory, context)
        }
      is AnnotationAttributeValueRequest.ArrayValue -> {
        val arrayExpressionText = value.members.joinToString {
          attributeRequestToValue(it, psiElementFactory, context, annotationRequest)?.text ?: ""
        }
        val dummyAnnotation = psiElementFactory.createAnnotationFromText("@dummy({$arrayExpressionText})", context)
        dummyAnnotation.findAttributeValue(null)
      }
      else -> {
        LOG.error("adding annotation members of ${value.javaClass} type is not implemented");
        null
      }
    }
  }
}

