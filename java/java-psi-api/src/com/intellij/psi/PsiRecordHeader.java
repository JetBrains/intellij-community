// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface PsiRecordHeader extends PsiElement {
  PsiRecordComponent @NotNull [] getRecordComponents();

  @Nullable
  PsiClass getContainingClass();
}
