// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType

private class SimpleMethodRequest(
  override val methodName: String,
  override val modifiers: Collection<JvmModifier> = emptyList(),
  override val returnType: ExpectedTypes = emptyList(),
  override val annotations: Collection<AnnotationRequest> = emptyList(),
  override val parameters: List<ExpectedParameter> = emptyList(),
  override val targetSubstitutor: JvmSubstitutor
) : CreateMethodRequest {
  override val isValid: Boolean = true
}

private class SimpleConstructorRequest(
  override val parameters: ExpectedParameters,
  override val targetSubstitutor: JvmSubstitutor
) : CreateConstructorRequest {
  override val isValid: Boolean get() = true
  override val modifiers: List<JvmModifier> get() = emptyList()
  override val annotations: Collection<AnnotationRequest> = emptyList()
}

fun methodRequest(project: Project, methodName: String, modifier: JvmModifier, returnType: JvmType): CreateMethodRequest {
  return SimpleMethodRequest(
    methodName = methodName,
    modifiers = listOf(modifier),
    returnType = listOf(expectedType(returnType)),
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}

fun constructorRequest(project: Project, parameters: List<Pair<String, PsiType>>): CreateConstructorRequest {
  val expectedParameters = parameters.map {
    nameInfo(it.first) to expectedTypes(it.second)
  }
  return SimpleConstructorRequest(
    parameters = expectedParameters,
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}
