// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.beanProperties

import com.intellij.lang.java.beans.PropertyKind
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.*
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.PsiType
import com.intellij.psi.util.PropertyUtilBase.getAccessorName

internal class CreateBeanPropertyRequest(
  project: Project,
  propertyName: String,
  propertyKind: PropertyKind,
  private val type: PsiType
) : CreateMethodRequest {

  private val isSetter: Boolean = propertyKind == PropertyKind.SETTER
  private val expectedTypes = expectedTypes(type)

  private val myMethodName = getAccessorName(propertyName, propertyKind)
  override fun getMethodName(): String = myMethodName

  private val myReturnType = if (isSetter) expectedTypes(PsiType.VOID) else expectedTypes
  override fun getReturnType() = myReturnType

  private val myModifiers = listOf(JvmModifier.PUBLIC)
  override fun getModifiers() = myModifiers

  override fun getAnnotations() = emptyList<AnnotationRequest>()

  private val myTargetSubstitutor = PsiJvmSubstitutor(project, PsiSubstitutor.EMPTY)
  override fun getTargetSubstitutor() = myTargetSubstitutor

  private val myParameters = if (isSetter) listOf(expectedParameter(type, propertyName)) else emptyList()
  override fun getExpectedParameters() = myParameters

  private val oldParameters = getParameters(myParameters, project)
  override fun getParameters() = oldParameters

  override fun isValid() = type.isValid
}
