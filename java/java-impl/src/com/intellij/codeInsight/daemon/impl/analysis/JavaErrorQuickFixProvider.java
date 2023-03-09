// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.analysis;

import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.QuickFixAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.impl.PriorityIntentionActionWrapper;
import com.intellij.codeInspection.ConvertRecordToClassFix;
import com.intellij.core.JavaPsiBundle;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class JavaErrorQuickFixProvider implements ErrorQuickFixProvider {
  private static final QuickFixFactory QUICK_FIX_FACTORY = QuickFixFactory.getInstance();

  @Override
  public void registerErrorQuickFix(@NotNull PsiErrorElement errorElement, @NotNull HighlightInfo.Builder info) {
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    List<IntentionAction> registrar = new ArrayList<>();
    if (parent instanceof PsiStatement && description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      info.registerFix(new InsertMissingTokenFix(";"), null, null, null, null);
      HighlightFixUtil.registerFixesForExpressionStatement((PsiStatement)parent, registrar);
    }
    if (parent instanceof PsiTryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      registrar.add(new AddExceptionToCatchFix(false));
      registrar.add(new AddFinallyFix((PsiTryStatement)parent));
    }
    if (parent instanceof PsiSwitchLabeledRuleStatement && description.equals(JavaPsiBundle.message("expected.switch.rule"))) {
      IntentionAction action =
        QUICK_FIX_FACTORY.createWrapSwitchRuleStatementsIntoBlockFix((PsiSwitchLabeledRuleStatement)parent);
      registrar.add(action);
    }
    if (parent instanceof PsiJavaFile && description.equals(JavaPsiBundle.message("expected.class.or.interface"))) {
      PsiElement child = errorElement.getFirstChild();
      if (child instanceof PsiIdentifier) {
        switch (child.getText()) {
          case PsiKeyword.RECORD -> {
            HighlightUtil.registerIncreaseLanguageLevelFixes(errorElement, HighlightingFeature.RECORDS, registrar);
            if (ConvertRecordToClassFix.tryMakeRecord(errorElement) != null) {
              IntentionAction action = PriorityIntentionActionWrapper.lowPriority(new ConvertRecordToClassFix(errorElement));
              registrar.add(action);
            }
          }
          case PsiKeyword.SEALED -> HighlightUtil.registerIncreaseLanguageLevelFixes(errorElement, HighlightingFeature.SEALED_CLASSES, registrar);
          default -> {
          }
        }
      }
    }
    QuickFixAction.registerQuickFixActions(info, null, registrar);
  }
}
