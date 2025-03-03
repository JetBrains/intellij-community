// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.daemon.impl.analysis.AbstractJavaErrorFixProvider;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightFixUtil;
import com.intellij.codeInsight.daemon.impl.quickfix.AddExceptionToCatchFix;
import com.intellij.codeInsight.daemon.impl.quickfix.AddFinallyFix;
import com.intellij.codeInsight.daemon.impl.quickfix.InsertMissingTokenFix;
import com.intellij.codeInsight.daemon.impl.quickfix.VariableAccessFromInnerClassJava10Fix;
import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInspection.streamMigration.SimplifyForEachInspection;
import com.intellij.core.JavaPsiBundle;
import com.intellij.java.codeserver.highlighting.errors.JavaErrorKinds;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Some quick-fixes not accessible from the java.analysis module are registered here.
 */
public final class AdditionalJavaErrorFixProvider extends AbstractJavaErrorFixProvider {
  public AdditionalJavaErrorFixProvider() {
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new SimplifyForEachInspection.ForEachNonFinalFix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_LAMBDA, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fix(JavaErrorKinds.VARIABLE_MUST_BE_EFFECTIVELY_FINAL_GUARD, error -> new VariableAccessFromInnerClassJava10Fix(error.psi()));
    fixes(JavaErrorKinds.SYNTAX_ERROR, (error, info) -> registerErrorElementFixes(info, error.psi()));
  }

  private static void registerErrorElementFixes(@NotNull Consumer<? super CommonIntentionAction> info,
                                                @NotNull PsiErrorElement errorElement) {
    PsiElement parent = errorElement.getParent();
    String description = errorElement.getErrorDescription();
    if (description.equals(JavaPsiBundle.message("expected.semicolon"))) {
      info.accept(new InsertMissingTokenFix(";"));
      HighlightFixUtil.registerFixesForExpressionStatement(info, parent);
    }
    if (parent instanceof PsiTryStatement tryStatement && description.equals(JavaPsiBundle.message("expected.catch.or.finally"))) {
      info.accept(new AddExceptionToCatchFix(false));
      info.accept(new AddFinallyFix(tryStatement));
    }
    if (parent instanceof PsiSwitchLabelStatementBase && description.equals(JavaPsiBundle.message("expected.colon.or.arrow"))) {
      PsiSwitchBlock switchBlock = PsiTreeUtil.getParentOfType(parent, PsiSwitchBlock.class);
      if (switchBlock != null && switchBlock.getBody() != null) {
        boolean isOld = false;
        boolean isRule = false;
        for (@NotNull PsiElement child : switchBlock.getBody().getChildren()) {
          if (child instanceof PsiSwitchLabeledRuleStatement) {
            isRule = true;
          }
          if (child instanceof PsiSwitchLabelStatement && !PsiTreeUtil.isAncestor(child, parent, false)) {
            isOld = true;
          }
        }
        if (isOld) {
          info.accept(new InsertMissingTokenFix(":", true));
        }
        if (isRule) {
          info.accept(new InsertMissingTokenFix(" ->", true));
        }
      }
    }
  }
}
