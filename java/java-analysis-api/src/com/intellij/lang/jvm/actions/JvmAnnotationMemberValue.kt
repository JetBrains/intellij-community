// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions


// TODO: maybe should be a part of JvmAnnotation
sealed class JvmAnnotationMemberValue {
  data class PrimitiveLiteral(val value: Any) : JvmAnnotationMemberValue()
  data class StringLiteral(val value: String) : JvmAnnotationMemberValue()
  data class ClassLiteral(val fqn: String) : JvmAnnotationMemberValue()
  data class EnumLiteral(val fqn: String) : JvmAnnotationMemberValue()
  data class NestedAnnotation(val annotationRequest: CreateAnnotationRequest) : JvmAnnotationMemberValue()
  data class NestedArray(val members: Array<JvmAnnotationMemberValue>) : JvmAnnotationMemberValue()
}