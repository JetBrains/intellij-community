// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor
import com.intellij.lang.jvm.types.JvmType
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

fun fieldRequest(
  name: String,
  substitutor: JvmSubstitutor,
  type: JvmType = PsiType.VOID,
  initializer: PsiElement? = null,
  modifiers: Collection<JvmModifier> = emptyList(),
  isConstant: Boolean = false
) = object : CreateFieldRequest {
  override fun isValid(): Boolean = true

  override fun getFieldName(): String = name

  override fun getFieldType(): List<ExpectedType> = expectedTypes(type)

  override fun getTargetSubstitutor(): JvmSubstitutor = substitutor

  override fun getInitializer(): PsiElement? = initializer

  override fun getModifiers(): Collection<JvmModifier> = modifiers

  override fun isConstant(): Boolean = isConstant
}