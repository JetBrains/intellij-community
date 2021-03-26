// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public abstract class HighlightingLevelManager {
  public static HighlightingLevelManager getInstance(Project project) {
    return project.getService(HighlightingLevelManager.class);
  }

  /**
   * >= Syntax level
   */
  public abstract boolean shouldHighlight(@NotNull PsiElement psiRoot);

  /**
   * >= Inspections level
   */
  public abstract boolean shouldInspect(@NotNull PsiElement psiRoot);
}
