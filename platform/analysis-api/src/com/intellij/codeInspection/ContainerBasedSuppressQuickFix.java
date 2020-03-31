// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;

/**
 * This kind of suppression fix is able to provide suppression container. 
 * Container might be used by the IDE for checking fix availability and highlighting suppression element.
 */
public interface ContainerBasedSuppressQuickFix extends SuppressQuickFix {
  @Nullable
  PsiElement getContainer(@Nullable PsiElement context);
}
