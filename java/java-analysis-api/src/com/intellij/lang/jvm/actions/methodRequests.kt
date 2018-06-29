// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.openapi.util.Pair as JBPair

private class SimpleMethodRequest(
  private val methodName: String,
  private val modifiers: Collection<JvmModifier>,
  private val returnType: ExpectedTypes,
  private val targetSubstitutor: JvmSubstitutor
) : CreateMethodRequest {
  override fun isValid(): Boolean = true
  override fun getMethodName() = methodName
  override fun getModifiers() = modifiers
  override fun getReturnType() = returnType
  override fun getAnnotations() = emptyList<AnnotationRequest>()
  override fun getExpectedParameters() = emptyList<ExpectedParameter>()
  override fun getTargetSubstitutor() = targetSubstitutor
}

private class SimpleConstructorRequest(
  private val expectedParameters: List<ExpectedParameter>,
  private val targetSubstitutor: JvmSubstitutor
) : CreateConstructorRequest {
  override fun isValid(): Boolean = true
  override fun getModifiers() = emptyList<JvmModifier>()
  override fun getAnnotations() = emptyList<AnnotationRequest>()
  override fun getTargetSubstitutor() = targetSubstitutor
  override fun getExpectedParameters() = expectedParameters
}

fun methodRequest(project: Project, methodName: String, modifier: JvmModifier, returnType: JvmType): CreateMethodRequest {
  return SimpleMethodRequest(
    methodName = methodName,
    modifiers = listOf(modifier),
    returnType = listOf(expectedType(returnType)),
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}

fun constructorRequest(project: Project, parameters: List<JBPair<String, PsiType>>): CreateConstructorRequest {
  return SimpleConstructorRequest(
    expectedParameters = parameters.map { expectedParameter(it.second, it.first) },
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}
