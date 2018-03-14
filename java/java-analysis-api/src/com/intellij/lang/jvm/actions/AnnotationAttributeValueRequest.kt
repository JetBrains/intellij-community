// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmClass


sealed class AnnotationAttributeValueRequest {
  data class PrimitiveValue(val value: Any) : AnnotationAttributeValueRequest()
  data class StringValue(val value: String) : AnnotationAttributeValueRequest()
  data class ClassValue(val clazz: JvmClass) : AnnotationAttributeValueRequest()
  data class ConstantValue(val reference: JvmField) : AnnotationAttributeValueRequest()
  data class NestedAnnotation(val annotationRequest: AnnotationRequest) : AnnotationAttributeValueRequest()
  data class ArrayValue(val members: List<AnnotationAttributeValueRequest>) : AnnotationAttributeValueRequest()
}

class AnnotationNamedAttributeRequest(val name: String, val value: AnnotationAttributeValueRequest)

fun stringAttribute(name: String, value: String) = AnnotationNamedAttributeRequest(name, AnnotationAttributeValueRequest.StringValue(value))
fun intAttribute(name: String, value: Int) = AnnotationNamedAttributeRequest(name, AnnotationAttributeValueRequest.PrimitiveValue(value))
