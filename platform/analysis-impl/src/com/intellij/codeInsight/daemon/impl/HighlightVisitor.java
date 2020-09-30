// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public interface HighlightVisitor {
  ExtensionPointName<HighlightVisitor> EP_HIGHLIGHT_VISITOR = new ExtensionPointName<>("com.intellij.highlightVisitor");

  boolean suitableForFile(@NotNull PsiFile file);

  void visit(@NotNull PsiElement element);

  boolean analyze(@NotNull PsiFile file, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action);

  @NotNull
  HighlightVisitor clone();

  /**
   * @deprecated unused, left for binary compatibility
   */
  @Deprecated
  default int order() { return -1; }
}
