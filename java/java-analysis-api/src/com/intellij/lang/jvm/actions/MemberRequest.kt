// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmType

sealed class MemberRequest {

  class Property(
    val propertyName: String,
    val visibilityModifier: JvmModifier,
    val propertyType: JvmType,
    val setterRequired: Boolean,
    val getterRequired: Boolean
  ) : MemberRequest()

  class Modifier(
    val modifier: JvmModifier,
    val shouldPresent: Boolean
  ) : MemberRequest()

}
