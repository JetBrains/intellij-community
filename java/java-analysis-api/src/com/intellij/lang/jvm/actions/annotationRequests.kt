// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmAnnotation

public fun annotationRequest(fqn: String, vararg parameters: AnnotationAttributeRequest): AnnotationRequest =
  SimpleAnnotationRequest(fqn, parameters.asList())

public fun annotationRequest(annotation: JvmAnnotation): AnnotationRequest? =
  annotation.qualifiedName?.let { SimpleAnnotationRequest(it, attributeRequests(annotation)) }

private class SimpleAnnotationRequest(private val fqn: String,
                                      private val attributes: List<AnnotationAttributeRequest>) : AnnotationRequest {
  override fun getQualifiedName(): String = fqn

  override fun getAttributes(): List<AnnotationAttributeRequest> = attributes

  override fun isValid(): Boolean = true
}
