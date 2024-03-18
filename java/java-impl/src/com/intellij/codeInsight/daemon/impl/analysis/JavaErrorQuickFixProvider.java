// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.core.JavaPsiBundle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class JavaErrorQuickFixProvider implements ErrorQuickFixProvider {
  @Override
  public void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo.Builder info) {
    if (!(errorElement.getLanguage() instanceof JavaLanguage)) return;
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    List<IntentionAction> registrar = new ArrayList<>();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      info.registerFix(new InsertMissingTokenFix(";"), null, null, null, null);
      HighlightFixUtil.registerFixesForExpressionStatement(parent, registrar);
    }
    if (parent instanceof PsiTryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      registrar.add(new AddExceptionToCatchFix(false).asIntention());
      registrar.add(new AddFinallyFix((PsiTryStatement)parent).asIntention());
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement && description.equals(JavaPsiBundle.message("expected.switch.rule"))) {
      IntentionAction action =
        QuickFixFactory.getInstance().createWrapSwitchRuleStatementsIntoBlockFix((PsiSwitchLabeledRuleStatement)parent);
      registrar.add(action);
    }
    QuickFixAction.registerQuickFixActions(info, null, registrar);
  }
}
