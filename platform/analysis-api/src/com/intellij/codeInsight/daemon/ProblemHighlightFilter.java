// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
import com.intellij.util.SlowOperations;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This filters can be used to prevent error highlighting (invalid code, unresolved references etc.) in files outside of project scope.
 *
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

  public static boolean shouldHighlightFile(@Nullable final PsiFile psiFile) {
    return SlowOperations.allowSlowOperations(() -> shouldProcess(psiFile, true));
  }

  public static boolean shouldProcessFileInBatch(@Nullable final PsiFile psiFile) {
    return shouldProcess(psiFile, false);
  }

  private static boolean shouldProcess(PsiFile psiFile, boolean onTheFly) {
    if (psiFile == null) return true;

    return ContainerUtil.all(EP_NAME.getExtensionList(), filter -> onTheFly ? filter.shouldHighlight(psiFile) : filter.shouldProcessInBatch(psiFile));
  }
}
