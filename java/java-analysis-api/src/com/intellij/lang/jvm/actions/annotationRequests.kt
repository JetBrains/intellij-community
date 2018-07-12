// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

fun annotationRequest(fqn: String, vararg parameters: AnnotationAttributeRequest): AnnotationRequest = object : AnnotationRequest {
  override fun getQualifiedName(): String = fqn

  override fun getAttributes(): List<AnnotationAttributeRequest> = parameters.asList()

  override fun isValid(): Boolean = true

}
