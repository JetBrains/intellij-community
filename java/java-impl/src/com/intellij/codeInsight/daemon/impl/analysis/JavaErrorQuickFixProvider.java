// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiSwitchLabeledRuleStatement;
import com.intellij.psi.PsiTryStatement;
import org.jetbrains.annotations.NotNull;

public class JavaErrorQuickFixProvider implements ErrorQuickFixProvider {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  @Override
  public void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo highlightInfo) {
    PsiElement parent = errorElement.getParent();
    if (parent instanceof PsiTryStatement && errorElement.getErrorDescription().equals(
      JavaErrorMessages.message("expected.catch.or.finally"))) {
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddExceptionToCatchFix(false));
      QuickFixAction.registerQuickFixAction(highlightInfo, new AddFinallyFix((PsiTryStatement)parent));
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement && errorElement.getErrorDescription().equals(
      JavaErrorMessages.message("expected.switch.rule"))) {
      QuickFixAction.registerQuickFixAction(
        highlightInfo, QUICK_FIX_FACTORY.createWrapSwitchRuleStatementsIntoBlockFix((PsiSwitchLabeledRuleStatement)parent));
    }
  }
}
