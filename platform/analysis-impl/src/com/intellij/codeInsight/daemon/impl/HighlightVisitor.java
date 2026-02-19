// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.impl.analysis.HighlightInfoHolder;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.PossiblyDumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;

/**
 * Apart from {@link com.intellij.codeInsight.daemon.RainbowVisitor},
 * prefer using {@link com.intellij.lang.annotation.Annotator} or {@link com.intellij.codeInspection.LocalInspectionTool}
 * to provide additional highlighting.
 */
public interface HighlightVisitor extends PossiblyDumbAware {
  HighlightVisitor [] EMPTY_ARRAY = new HighlightVisitor[0];
  ArrayFactory<HighlightVisitor> ARRAY_FACTORY = count -> count == 0 ? EMPTY_ARRAY : new HighlightVisitor[count];
  ExtensionPointName<HighlightVisitor> EP_HIGHLIGHT_VISITOR = new ExtensionPointName<>("com.intellij.highlightVisitor");

  boolean suitableForFile(@NotNull PsiFile psiFile);

  /**
   * @return true if this highlighter covers the errors reported by {@link DefaultHighlightVisitor}, so the latter should be turned off.
   */
  default boolean supersedesDefaultHighlighter() {
    return false;
  }

  /**
   * The main highlighting method, which is called for each PSI element within the range to be highlighted.
   * Please make sure the implementation of this method is creating highlighters with the text range lying within the current PSI element passed to the visitor,
   * to minimize annoying flickering and inconsistencies.
   */
  void visit(@NotNull PsiElement element);

  boolean analyze(@NotNull PsiFile psiFile, boolean updateWholeFile, @NotNull HighlightInfoHolder holder, @NotNull Runnable action);

  @NotNull
  HighlightVisitor clone();

  /**
   * @deprecated unused, left for binary compatibility
   */
  @Deprecated
  default int order() { return -1; }
}
