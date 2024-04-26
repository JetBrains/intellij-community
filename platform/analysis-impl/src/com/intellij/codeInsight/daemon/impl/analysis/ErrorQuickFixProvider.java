// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiErrorElement;
import org.jetbrains.annotations.NotNull;

public interface ErrorQuickFixProvider extends PossiblyDumbAware {
  ExtensionPointName<ErrorQuickFixProvider> EP_NAME = ExtensionPointName.create("com.intellij.errorQuickFixProvider");

  /**
   * @deprecated implement {@link #registerErrorQuickFix(PsiErrorElement, HighlightInfo.Builder)} instead
   */
  @Deprecated(forRemoval = true)
  default void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo highlightInfo) {

  }

  default void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo.Builder builder) {
  }
}
