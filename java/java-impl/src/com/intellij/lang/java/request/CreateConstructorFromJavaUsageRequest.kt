// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.request

import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.psi.PsiConstructorCall

internal class CreateConstructorFromJavaUsageRequest(
  call: PsiConstructorCall,
  override val modifiers: Collection<JvmModifier>
) : CreateExecutableFromJavaUsageRequest<PsiConstructorCall>(call), CreateConstructorRequest
