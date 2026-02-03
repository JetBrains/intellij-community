// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.dupLocator;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface DuplocateVisitor {
  void visitNode(@NotNull PsiElement node);

  /**
   * Is not invoked when index is used
   * @see DuplicatesProfile#supportIndex()
   */
  void hashingFinished();
}
