/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.pom;

import com.intellij.psi.ElementDescriptionProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ElementDescriptionLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author peter
 */
public abstract class PomDescriptionProvider implements ElementDescriptionProvider{
  @Nullable
  public String getElementDescription(@NotNull PsiElement element, @NotNull ElementDescriptionLocation location) {
    if (element instanceof PomTargetPsiElement) {
      return getElementDescription(((PomTargetPsiElement)element).getTarget(), location);
    }
    return null;
  }

  @Nullable
  public abstract String getElementDescription(@NotNull PomTarget element, @NotNull ElementDescriptionLocation location);
}
