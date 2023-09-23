// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.ElementDescriptionLocation;
import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class PomDescriptionProvider implements ElementDescriptionProvider{
  @Override
  public @Nullable String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (element instanceof PomTargetPsiElement) {
      return getElementDescription(((PomTargetPsiElement)element).getTarget(), location);
    }
    return null;
  }

  public abstract @Nullable @NlsSafe String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location);
}
