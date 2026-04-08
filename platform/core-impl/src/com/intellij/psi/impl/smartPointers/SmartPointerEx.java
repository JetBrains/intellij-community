// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.smartPointers;

import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public interface SmartPointerEx<E extends PsiElement> extends SmartPsiElementPointer<E> {

  /**
   * @return the internally cached element. It can be invalid.
   */
  @Nullable
  PsiElement getCachedElement();
}
