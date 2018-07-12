// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions


sealed class AnnotationAttributeValueRequest {
  data class PrimitiveValue(val value: Any) : AnnotationAttributeValueRequest()
  data class StringValue(val value: String) : AnnotationAttributeValueRequest()
  /*
  There also could be additional cases namely:
  ```
     data class ClassValue(val clazz: JvmClass) : AnnotationAttributeValueRequest()
     data class ConstantValue(val reference: JvmField) : AnnotationAttributeValueRequest()
     data class NestedAnnotation(val annotationRequest: AnnotationRequest) : AnnotationAttributeValueRequest()
     data class ArrayValue(val members: List<AnnotationAttributeValueRequest>) : AnnotationAttributeValueRequest()
  ```
  they are skipped until become necessary
   */

}

data class AnnotationAttributeRequest(val name: String, val value: AnnotationAttributeValueRequest)

fun stringAttribute(name: String, value: String): AnnotationAttributeRequest = AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.StringValue(value))
fun intAttribute(name: String, value: Int): AnnotationAttributeRequest = AnnotationAttributeRequest(name, AnnotationAttributeValueRequest.PrimitiveValue(value))
