// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.extractMethod.newImpl.structures.InputParameter

class SignatureBuilder(project: Project) {
  private val factory: PsiElementFactory = PsiElementFactory.getInstance(project)

  fun build(
    isStatic: Boolean = false,
    visibility: String,
    typeParameters: PsiTypeParameterList,
    returnType: PsiType? = null,
    methodName: String = "extracted",
    inputParameters: List<InputParameter> = emptyList(),
    thrownExceptions: List<PsiClassType> = emptyList()
  ): PsiMethod {
    val parameterList = factory.createParameterList(
      inputParameters.map { it.name }.toTypedArray(),
      inputParameters.map { it.type }.toTypedArray()
    )
    val method = when (returnType) {
      null -> factory.createConstructor()
      else -> factory.createMethod(methodName, returnType)
    }
    method.typeParameterList?.replace(typeParameters)
    method.parameterList.replace(parameterList)
    method.modifierList.setModifierProperty(PsiModifier.STATIC, isStatic)
    method.modifierList.setModifierProperty(visibility, true)
    thrownExceptions.map { exception -> factory.createReferenceElementByType(exception) }.forEach { method.throwsList.add(it) }
    return method
  }

}