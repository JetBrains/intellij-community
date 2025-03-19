// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

public interface SuppressQuickFix extends LocalQuickFix {
  SuppressQuickFix[] EMPTY_ARRAY = new SuppressQuickFix[0];
  boolean isAvailable(final @NotNull Project project, final @NotNull PsiElement context);

  /// Indicates if the fix suppresses all warnings.
  ///
  /// When true, the fix will be put at the bottom of the list,
  /// ignoring any other priority set by the [#getPriority()] method below.
  boolean isSuppressAll();

  /// Value used to sort quick-fixes.
  /// - Quick-fixes with bigger priorities will appear last
  /// - Quick-fixes with the same priority will be sorted alphabetically
  default int getPriority() {
    return Integer.MAX_VALUE;
  }
}
