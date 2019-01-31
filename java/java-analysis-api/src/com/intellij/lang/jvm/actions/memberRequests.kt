// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier

fun modifierRequest(modifier: JvmModifier, shouldBePresent: Boolean) = object : ChangeModifierRequest {
  override fun isValid(): Boolean = true
  override fun getModifier(): JvmModifier = modifier
  override fun shouldBePresent(): Boolean = shouldBePresent
}