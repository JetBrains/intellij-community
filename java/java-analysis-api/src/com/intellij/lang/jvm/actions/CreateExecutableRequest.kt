// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.jvm.actions

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.types.JvmSubstitutor

interface CreateExecutableRequest {

  val isValid: Boolean

  val modifiers: Collection<JvmModifier>

  val annotations: Collection<AnnotationRequest>

  val targetSubstitutor: JvmSubstitutor

  val parameters: ExpectedParameters
}
