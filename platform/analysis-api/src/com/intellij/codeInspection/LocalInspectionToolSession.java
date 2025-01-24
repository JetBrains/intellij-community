// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection;

import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The context of the {@link LocalInspectionTool} life cycle.
 * @see LocalInspectionTool#buildVisitor(ProblemsHolder, boolean, LocalInspectionToolSession)
 */
public final class LocalInspectionToolSession extends UserDataHolderBase {
  private final PsiFile myFile;
  private final TextRange myPriorityRange;
  private final TextRange myRestrictRange;
  private final HighlightSeverity myMinimumSeverity;

  LocalInspectionToolSession(@NotNull PsiFile file, @NotNull TextRange priorityRange, @NotNull TextRange restrictRange,
                             @Nullable HighlightSeverity minimumSeverity) {
    myFile = file;
    myPriorityRange = priorityRange;
    myRestrictRange = restrictRange;
    myMinimumSeverity = minimumSeverity;
  }

  public @NotNull PsiFile getFile() {
    return myFile;
  }

  /**
   * @return range (inside the {@link #getFile()}) which the current session will try to highlight first.
   * Usually it corresponds to the visible view port in the editor.
   */
  public @NotNull TextRange getPriorityRange() {
    return myPriorityRange;
  }

  /**
   * @return range (inside the {@link #getFile()}) which the current session will restrict itself to.
   */
  public @NotNull TextRange getRestrictRange() {
    return myRestrictRange;
  }

  /**
   * @return Minimum Severity (or null if not specified) which is a hint that suggests what highlighting level is requested
   * from this inspection in this specific inspection session.
   * For example, "code smell detector" called on VCS commit might request ERROR/WARNING only and ignore INFORMATION annotations.
   * Knowing this minimum requested severity, the corresponding inspection might react by skipping part of the (potentially expensive) work.
   * For example, spellchecker plugin might want to skip running itself altogether if minimumSeverity = WARNING.
   * This hint is only a hint, meaning that the inspection might choose to ignore it.
   */
  public @Nullable HighlightSeverity getMinimumSeverity() {
    return myMinimumSeverity;
  }
}
