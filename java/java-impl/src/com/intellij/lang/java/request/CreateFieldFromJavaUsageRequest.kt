// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.getTargetSubstitutor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.guessExpectedTypes
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJvmSubstitutor
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.util.createSmartPointer

internal class CreateFieldFromJavaUsageRequest(
  reference: PsiReferenceExpression,
  private val modifiers: Collection<JvmModifier>,
  private val isConstant: Boolean,
  private val useAnchor: Boolean
) : CreateFieldRequest {

  private val myReference = reference.createSmartPointer()

  override fun isValid() = myReference.element?.referenceName != null

  val reference get() = myReference.element!!

  val anchor: PsiElement? get() = if (useAnchor) reference else null

  override fun getModifiers() = modifiers

  override fun getFieldName() = reference.referenceName!!

  override fun getFieldType() = guessExpectedTypes(reference, false).map(::ExpectedJavaType)

  override fun getTargetSubstitutor() = PsiJvmSubstitutor(reference.project, getTargetSubstitutor(reference))

  override fun isConstant(): Boolean = isConstant
}
