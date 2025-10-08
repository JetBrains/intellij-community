// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.dependency

import com.intellij.psi.PsiModifierListOwner
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
public interface KotlinExtensionMemberChecker {
  public fun check(target: PsiModifierListOwner): Boolean
}
