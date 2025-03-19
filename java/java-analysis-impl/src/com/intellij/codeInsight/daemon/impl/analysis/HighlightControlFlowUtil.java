// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.controlFlow.AnalysisCanceledException;
import com.intellij.psi.controlFlow.ControlFlow;
import com.intellij.psi.controlFlow.ControlFlowFactory;
import com.intellij.psi.controlFlow.ControlFlowUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @deprecated all the methods are deprecated 
 */
@Deprecated
public final class HighlightControlFlowUtil {

  private HighlightControlFlowUtil() { }

  /**
   * @deprecated use {@link ControlFlowFactory#getControlFlowNoConstantEvaluate(PsiElement)}
   */
  @Deprecated
  public static @NotNull ControlFlow getControlFlowNoConstantEvaluate(@NotNull PsiElement body) throws AnalysisCanceledException {
    return ControlFlowFactory.getControlFlowNoConstantEvaluate(body);
  }

  /**
   * @deprecated use {@link ControlFlowUtil#variableDefinitelyAssignedIn(PsiVariable, PsiElement)}
   */
  @Deprecated
  public static boolean variableDefinitelyAssignedIn(@NotNull PsiVariable variable, @NotNull PsiElement context) {
    return ControlFlowUtil.variableDefinitelyAssignedIn(variable, context);
  }
}
