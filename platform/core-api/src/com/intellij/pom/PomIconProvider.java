// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.pom;

import com.intellij.ide.IconProvider;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public abstract class PomIconProvider extends IconProvider {
  @Override
  public Icon getIcon(@NotNull PsiElement element, int flags) {
    if (element instanceof PomTargetPsiElement) {
      return getIcon(((PomTargetPsiElement)element).getTarget(), flags);
    }
    return null;
  }

  public abstract @Nullable Icon getIcon(@NotNull PomTarget target, int flags);
}
