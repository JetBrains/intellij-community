// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageBaseFix.getTargetSubstitutor
import com.intellij.codeInsight.daemon.impl.quickfix.CreateFromUsageUtils.guessExpectedTypes
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmValue
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.openapi.util.NlsSafe
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

  override fun isValid(): Boolean = myReference.element?.referenceName != null

  val reference: PsiReferenceExpression get() = myReference.element!!

  val anchor: PsiElement? get() = if (useAnchor) reference else null

  override fun getAnnotations(): Collection<AnnotationRequest> = emptyList()

  override fun getModifiers(): Collection<JvmModifier> = modifiers

  override fun getFieldName(): @NlsSafe String = reference.referenceName!!

  override fun getFieldType(): List<ExpectedJavaType> = guessExpectedTypes(reference, false).map(::ExpectedJavaType)

  override fun getTargetSubstitutor(): PsiJvmSubstitutor = PsiJvmSubstitutor(reference.project, getTargetSubstitutor(reference))

  override fun isConstant(): Boolean = isConstant

  override fun getInitializer(): JvmValue? = null
}
