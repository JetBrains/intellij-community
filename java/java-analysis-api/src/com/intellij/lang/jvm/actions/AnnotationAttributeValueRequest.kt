// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmAnnotation
import com.intellij.lang.jvm.JvmField
import com.intellij.lang.jvm.annotation.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameValuePair


public sealed class AnnotationAttributeValueRequest {
  public data class PrimitiveValue(val value: Any) : AnnotationAttributeValueRequest()
  public data class StringValue(val value: String) : AnnotationAttributeValueRequest()
  public data class ClassValue(val classFqn: String) : AnnotationAttributeValueRequest()
  public data class ConstantValue(val reference: JvmField?, val text: String) : AnnotationAttributeValueRequest()
  public data class NestedAnnotation(val annotationRequest: AnnotationRequest) : AnnotationAttributeValueRequest()
  public data class ArrayValue(val members: List<AnnotationAttributeValueRequest>) : AnnotationAttributeValueRequest()
}

public data class AnnotationAttributeRequest(val name: String, val value: AnnotationAttributeValueRequest)

public fun stringAttribute(name: String, value: String): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.StringValue(value))

public fun intAttribute(name: String, value: Int): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.PrimitiveValue(value))

public fun classAttribute(name: String, classFqn: String): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.ClassValue(classFqn))

public fun constantAttribute(name: String, reference: JvmField): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.ConstantValue(reference, (reference as PsiElement).text))

public fun constantAttribute(name: String, refText: String): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.ConstantValue(null, refText))

public fun nestedAttribute(name: String, annotation: AnnotationRequest): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.NestedAnnotation(annotation))

public fun arrayAttribute(name: String, members: List<AnnotationAttributeValueRequest>): AnnotationAttributeRequest =
  AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.ArrayValue(members))

public fun attributeValueRequest(attribValue: JvmAnnotationAttributeValue,
                          attribute: JvmAnnotationAttribute? = null): AnnotationAttributeValueRequest? = when (attribValue) {
  is JvmAnnotationArrayValue -> AnnotationAttributeValueRequest.ArrayValue(
    attribValue.values.mapNotNull { attributeValueRequest(it, attribute) })
  is JvmAnnotationClassValue -> attribValue.clazz?.qualifiedName?.let(AnnotationAttributeValueRequest::ClassValue)
  is JvmAnnotationConstantValue -> when (val constantVal = attribValue.constantValue) {
    is String -> AnnotationAttributeValueRequest.StringValue(constantVal)
    is Any -> AnnotationAttributeValueRequest.PrimitiveValue(constantVal)
    else -> null
  }
  is JvmAnnotationEnumFieldValue -> {
    // Try to preserve the original text, otherwise fallback to field name
    val referenceText = (attribute as? PsiNameValuePair)?.value?.text ?: (attribValue.field as? PsiElement)?.text
    referenceText?.let { AnnotationAttributeValueRequest.ConstantValue(attribValue.field, it) }
  }
  is JvmNestedAnnotationValue -> annotationRequest(attribValue.value)?.let(AnnotationAttributeValueRequest::NestedAnnotation)
  else -> null
}

public fun attributeRequest(attribute: JvmAnnotationAttribute): AnnotationAttributeRequest? {
  val attribValue = attribute.attributeValue ?: return null
  val valueRequest = attributeValueRequest(attribValue, attribute) ?: return null
  return AnnotationAttributeRequest(attribute.attributeName, valueRequest)
}

public fun attributeRequests(annotation: JvmAnnotation): List<AnnotationAttributeRequest> =
  annotation.attributes.mapNotNull(::attributeRequest)
