// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.javadoc;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the name of a fragment reference, for instance {@code my-id} in {@code Foo##my-id}.
 */
public interface PsiDocFragmentName extends PsiElement {

  /**
   * Returns the {@link PsiClass} or {@link PsiPackage} that contains the target fragment.
   */
  @Nullable PsiElement getScope();

}
