// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.EnsureInstruction;
import com.intellij.codeInspection.dataFlow.lang.ir.inst.ExpressionPushingInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * A language-specific support to aid DFA analysis
 * @param <EXPR> type of expression element in the language
 */
public interface DfaLanguageSupport<EXPR extends PsiElement> {

  /**
   * Process interceptor calls on {@link ExpressionPushingInstruction}.
   * @param interceptor interceptor to delegate to
   * @param value value to be pushed
   * @param instruction expression pushing instruction
   * @param state memory state
   */
  void processExpressionPush(@NotNull DfaInterceptor<EXPR> interceptor,
                             @NotNull DfaValue value,
                             @NotNull ExpressionPushingInstruction<?> instruction,
                             @NotNull DfaMemoryState state);

  /**
   * Calls interceptor on condition failure
   * @param interceptor interceptor to delegate to
   * @param instruction instruction whose condition is failed
   * @param alwaysFails if true then the condition fails always
   */
  void processConditionFailure(@NotNull DfaInterceptor<EXPR> interceptor, @NotNull EnsureInstruction instruction, boolean alwaysFails);
}
