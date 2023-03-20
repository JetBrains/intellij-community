// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public class DefaultElementStatusTracker implements ElementStatusTracker {
  @Override
  public @NotNull FileStatus getElementStatus(@NotNull PsiElement element) {
    return FileStatus.NOT_CHANGED;
  }
}
