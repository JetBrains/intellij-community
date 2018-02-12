// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.util.PsiUtil

internal fun <T : PsiModifierListOwner> T.setStatic(isStatic: Boolean) = apply {
  PsiUtil.setModifierProperty(this, PsiModifier.STATIC, isStatic)
}
