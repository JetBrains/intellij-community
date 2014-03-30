/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.daemon;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiFile;
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
    return shouldProcess(psiFile, true);
  }

  public static boolean shouldProcessFileInBatch(@Nullable final PsiFile psiFile) {
    return shouldProcess(psiFile, false);
  }

  private static boolean shouldProcess(PsiFile psiFile, boolean onTheFly) {
    if (psiFile == null) return true;

    final ProblemHighlightFilter[] filters = EP_NAME.getExtensions();
    for (ProblemHighlightFilter filter : filters) {
      if (onTheFly ? !filter.shouldHighlight(psiFile) : !filter.shouldProcessInBatch(psiFile)) return false;
    }

    return true;
  }
}
