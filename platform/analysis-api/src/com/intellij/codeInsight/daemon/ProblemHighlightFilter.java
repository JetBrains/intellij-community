// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * This filters can be used to prevent error highlighting (invalid code, unresolved references etc.) in files outside of project scope.
 * Filter implementations should be permissive - i.e. should prevent highlighting only for files it absolutely knows about,
 * and return true otherwise.
 */
public abstract class ProblemHighlightFilter {
  public static final ExtensionPointName<ProblemHighlightFilter> EP_NAME = ExtensionPointName.create("com.intellij.problemHighlightFilter");

  /**
   * @param psiFile file to decide about
   * @return false if this filter disables highlighting for given file, true if filter enables highlighting or can't decide
   */
  public abstract boolean shouldHighlight(@NotNull PsiFile psiFile);

  public boolean shouldProcessInBatch(@NotNull PsiFile psiFile) {
    return shouldHighlight(psiFile);
  }

  public static boolean shouldHighlightFile(final @NotNull PsiFile psiFile) {
    return shouldProcess(psiFile, true);
  }

  public static boolean shouldProcessFileInBatch(final @NotNull PsiFile psiFile) {
    return shouldProcess(psiFile, false);
  }

  private static boolean shouldProcess(@NotNull PsiFile psiFile, boolean onTheFly) {
    return ContainerUtil.all(EP_NAME.getExtensionList(), filter -> onTheFly ? filter.shouldHighlight(psiFile) : filter.shouldProcessInBatch(psiFile));
  }
}
