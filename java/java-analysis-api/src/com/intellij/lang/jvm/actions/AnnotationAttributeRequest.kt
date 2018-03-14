// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmClass


sealed class AnnotationAttributeRequest {
  data class PrimitiveLiteral(val value: Any) : AnnotationAttributeRequest()
  data class StringLiteral(val value: String) : AnnotationAttributeRequest()
  data class ClassValue(val clazz: JvmClass) : AnnotationAttributeRequest()
  data class ConstantValue(val reference: JvmField) : AnnotationAttributeRequest()
  data class NestedAnnotation(val annotationRequest: AnnotationRequest) : AnnotationAttributeRequest()
  data class NestedArray(val members: List<AnnotationAttributeRequest>) : AnnotationAttributeRequest()
}

class AnnotationNamedAttributeRequest(val name: String, val value: AnnotationAttributeRequest)

fun stringAttribute(name: String, value: String) = AnnotationNamedAttributeRequest(name, AnnotationAttributeRequest.StringLiteral(value))
fun intAttribute(name: String, value: Int) = AnnotationNamedAttributeRequest(name, AnnotationAttributeRequest.PrimitiveLiteral(value))
