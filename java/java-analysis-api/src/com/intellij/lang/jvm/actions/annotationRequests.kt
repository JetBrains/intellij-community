// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

private data class SimpleAnnotationRequest(private val qualifiedName: String) : AnnotationRequest {
  override fun getQualifiedName() = qualifiedName
}

fun annotationRequest(fqn: String): AnnotationRequest = SimpleAnnotationRequest(fqn)
