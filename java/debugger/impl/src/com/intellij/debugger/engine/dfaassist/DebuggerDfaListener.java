// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * A DFAListener to gather DFAAssist hints that should be displayed
 */
public interface DebuggerDfaListener extends DfaListener {
  /**
   * Compute final hints to display, after dataflow analysis is completed successfully
   *
   * @return map whose keys are PSI elements (e.g., boolean expression) and values are the corresponding hints (e.g., {@link DfaHint#TRUE})
   */
  @NotNull Map<PsiElement, DfaHint> computeHints();

  default @NotNull Collection<TextRange> unreachableSegments(@NotNull PsiElement startAnchor, @NotNull Set<PsiElement> unreachableElements) {
    return Collections.emptyList();
  }
}
