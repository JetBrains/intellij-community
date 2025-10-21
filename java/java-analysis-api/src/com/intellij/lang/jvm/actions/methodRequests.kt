// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmMethod
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.lang.jvm.types.JvmType
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import java.util.function.Function
import java.util.function.Supplier
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

private class SimpleTypeRequest(private val fqn: String?, private val annotations: List<AnnotationRequest>): ChangeTypeRequest {
  override fun isValid(): Boolean = true
  
  override fun getQualifiedName(): String? = fqn

  override fun getAnnotations(): List<AnnotationRequest> = annotations
}

public fun methodRequest(project: Project, methodName: String, modifiers: List<JvmModifier>, returnType: JvmType): CreateMethodRequest {
  return SimpleMethodRequest(
    methodName = methodName,
    modifiers = modifiers,
    returnType = listOf(expectedType(returnType)),
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}

public fun constructorRequest(project: Project, parameters: List<JBPair<String, PsiType>>): CreateConstructorRequest {
  return SimpleConstructorRequest(
    expectedParameters = parameters.map { expectedParameter(it.second, it.first) },
    targetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  )
}

public fun typeRequest(fqn: String?, annotations: List<AnnotationRequest>): ChangeTypeRequest = 
  SimpleTypeRequest(fqn, annotations)

public fun setMethodParametersRequest(parameters: Iterable<Map.Entry<String, JvmType>>): ChangeParametersRequest =
  SimpleChangeParametersRequest(parameters.map { expectedParameter(it.value, it.key) })

public fun updateMethodParametersRequest(parametersOwnerPointer: Supplier<JvmMethod?>,
                                  updateFunction: Function<List<ExpectedParameter>, List<ExpectedParameter>>): ChangeParametersRequest =
  UpdateParametersRequest(parametersOwnerPointer, updateFunction)

private class SimpleChangeParametersRequest(private val parameters: List<ExpectedParameter>) : ChangeParametersRequest {
  override fun getExpectedParameters(): List<ExpectedParameter> = parameters

  override fun isValid(): Boolean = true

}

private class UpdateParametersRequest(val parametersOwnerPointer: Supplier<JvmMethod?>,
                                      val updateFunction: Function<List<ExpectedParameter>, List<ExpectedParameter>>) : ChangeParametersRequest {

  override fun getExpectedParameters(): List<ExpectedParameter> {
    val jvmMethod = parametersOwnerPointer.get()
                    ?: throw IllegalStateException("parametersOwnerPointer is invalid, please check isValid() before calling this method")
    return updateFunction.apply(jvmMethod.parameters.map { ChangeParametersRequest.ExistingParameterWrapper(it) })
  }

  override fun isValid(): Boolean = parametersOwnerPointer.get() != null

}
