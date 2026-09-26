// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.lang.jvm.actions.AnnotationAttributeValueRequest
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiAnnotationMemberValue
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory

internal object CreateAnnotationActionUtil {
  private val LOG = logger<CreateAnnotationActionUtil>()
  internal fun attributeRequestToValue(value: AnnotationAttributeValueRequest,
                                       psiElementFactory: PsiElementFactory,
                                       context: PsiElement?): PsiAnnotationMemberValue? = when (value) {
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
        attributeRequestToValue(it, psiElementFactory, context)?.text ?: ""
      }
      val dummyAnnotation = psiElementFactory.createAnnotationFromText("@dummy({$arrayExpressionText})", context)
      dummyAnnotation.findAttributeValue(null)
    }
    else -> {
      LOG.error("adding annotation members of ${value.javaClass} type is not implemented")
      null
    }
  }

  internal fun fillAnnotationAttributes(annotation: PsiAnnotation,
                                       annotationRequest: AnnotationRequest,
                                       psiElementFactory: PsiElementFactory,
                                       context: PsiElement?) {
    for ((name, value) in annotationRequest.attributes) {
      val memberValue = attributeRequestToValue(value, psiElementFactory, context)
      annotation.setDeclaredAttributeValue(name.takeIf { name != PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME }, memberValue)
    }
  }
}
