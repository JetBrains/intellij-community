// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.lang.DfaListener;
import com.intellij.codeInspection.dataFlow.lang.ir.ControlFlow;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.xdebugger.impl.dfaassist.DfaHint;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

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

  /**
   * @param startAnchor         an anchor returned from {@link DfaAssistProvider#getAnchor(PsiElement)}, which created this listener
   * @param unreachableElements list of all {@link PsiElement} which were never visited during IR interpretation. This means that no
   *                            instruction between {@link ControlFlow#startElement(PsiElement)} and
   *                            {@link ControlFlow#finishElement(PsiElement)} was reached. Elements before startAnchor in IR are not
   *                            considered unreachable and not added to this set.
   * @return collection of text ranges to highlight as unreachable.
   */
  default @NotNull Collection<TextRange> unreachableSegments(@NotNull PsiElement startAnchor, @NotNull Set<PsiElement> unreachableElements) {
    return Collections.emptyList();
  }
}
