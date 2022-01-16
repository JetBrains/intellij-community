// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.*;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.ConvertRecordToClassFix;
import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class JavaErrorQuickFixProvider implements ErrorQuickFixProvider {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  @Override
  public void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo info) {
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      QuickFixAction.registerQuickFixAction(info, new InsertMissingTokenFix(";"));
      HighlightFixUtil.registerFixesForExpressionStatement(info, parent);
    }
    if (parent instanceof PsiTryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      QuickFixAction.registerQuickFixAction(info, new AddExceptionToCatchFix(false));
      QuickFixAction.registerQuickFixAction(info, new AddFinallyFix((PsiTryStatement)parent));
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement && description.equals(JavaPsiBundle.message("expected.switch.rule"))) {
      QuickFixAction.registerQuickFixAction(
        info, QUICK_FIX_FACTORY.createWrapSwitchRuleStatementsIntoBlockFix((PsiSwitchLabeledRuleStatement)parent));
    }
    if (parent instanceof PsiJavaFile && description.equals(JavaPsiBundle.message("expected.class.or.interface"))) {
      PsiElement child = errorElement.getFirstChild();
      if (child instanceof PsiIdentifier) {
        switch (child.getText()) {
          case PsiKeyword.RECORD:
            HighlightUtil.registerIncreaseLanguageLevelFixes(errorElement, HighlightingFeature.RECORDS, new QuickFixActionRegistrarImpl(info));
            if (ConvertRecordToClassFix.tryMakeRecord(errorElement) != null) {
              QuickFixAction.registerQuickFixAction(info, PriorityIntentionActionWrapper.lowPriority(new ConvertRecordToClassFix(errorElement)));
            }
            break;
          case PsiKeyword.SEALED:
            HighlightUtil.registerIncreaseLanguageLevelFixes(errorElement, HighlightingFeature.SEALED_CLASSES, new QuickFixActionRegistrarImpl(info));
            break;
          default:
            break;
        }
      }
    }
  }
}
