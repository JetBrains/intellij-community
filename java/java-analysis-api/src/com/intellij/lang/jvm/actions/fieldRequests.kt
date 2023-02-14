// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.JvmValue
import com.intellij.lang.jvm.types.JvmSubstitutor

internal class SimpleFieldRequest(
  private val fieldName: String,
  private val annotations: Collection<AnnotationRequest>,
  private val modifiers: Collection<JvmModifier>,
  private val fieldType: ExpectedTypes,
  private val targetSubstitutor: JvmSubstitutor,
  private val initializer: JvmValue? = null,
  private val isConstant: Boolean,
) : CreateFieldRequest {
  override fun isValid(): Boolean = true
  override fun getFieldName() = fieldName
  override fun getAnnotations() = annotations
  override fun getModifiers() = modifiers
  override fun getFieldType() = fieldType
  override fun getTargetSubstitutor() = targetSubstitutor
  override fun getInitializer(): JvmValue? = initializer
  override fun isConstant(): Boolean = isConstant
}

fun fieldRequest(
  fieldName: String,
  annotations: Collection<AnnotationRequest>,
  modifiers: Collection<JvmModifier>,
  fieldType: ExpectedTypes,
  targetSubstitutor: JvmSubstitutor,
  initializer: JvmValue?,
  isConstant: Boolean,
): CreateFieldRequest = SimpleFieldRequest(fieldName, annotations, modifiers, fieldType, targetSubstitutor, initializer, isConstant)