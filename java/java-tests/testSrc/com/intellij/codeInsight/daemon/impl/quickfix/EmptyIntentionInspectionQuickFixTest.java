// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.codeInsight.intention.EmptyIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.defUse.DefUseInspection;
import com.intellij.codeInspection.ex.EditInspectionToolsSettingsAction;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiLiteralExpression;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class EmptyIntentionInspectionQuickFixTest extends LightQuickFixTestCase {
  @Override
  @NonNls
  protected String getBasePath() {
    return "/codeInsight/daemonCodeAnalyzer/quickFix/emptyIntention";
  }

  @Override
  protected LocalInspectionTool @NotNull [] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{new DefUseInspection(), new LocalInspectionTool() {
      @Override
      @Nls
      @NotNull
      public String getGroupDisplayName() {
        return "MyGroup";
      }

      @Override
      @Nls
      @NotNull
      public String getDisplayName() {
        return "My";
      }

      @Override
      @NonNls
      @NotNull
      public String getShortName() {
        return getDisplayName();
      }

      @Override
      @NotNull
      public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new JavaElementVisitor() {
          @Override public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
            final String s = (String)expression.getValue();
            if (s.contains("a")) holder.registerProblem(expression, "Look ma! This String contains 'a'");
            if (s.contains("b")) holder.registerProblem(expression, "Look ma! This String contains 'b'");
          }
        };
      }
    }};
  }

  public void testX() {
    configureByFile(getBasePath()+"/X.java");
    List<IntentionAction> emptyActions = getAvailableActions();
    for (int i = emptyActions.size()-1; i>=0; i--) {
      IntentionAction action = emptyActions.get(i);
      action = IntentionActionDelegate.unwrap(action);
      if (!(action instanceof EmptyIntentionAction)) emptyActions.remove(i);
    }
    assertEquals(emptyActions.toString(), 1, emptyActions.size());
  }

  public void testLowPriority() {
    configureByFile(getBasePath() + "/LowPriority.java");
    List<IntentionAction> emptyActions = getAvailableActions();
    int i = 0;
    for(;i < emptyActions.size(); i++) {
      IntentionAction action = IntentionActionDelegate.unwrap(emptyActions.get(i));
      if ("Make 'i' not final".equals(action.getText())) {
        break;
      }
      if (action instanceof EmptyIntentionAction) {
        fail("Low priority action prior to quick fix");
      }
    }
    assertTrue(i < emptyActions.size());
    for (; i < emptyActions.size(); i++) {
      IntentionAction action = IntentionActionDelegate.unwrap(emptyActions.get(i));
      if (action instanceof EditInspectionToolsSettingsAction) {
        return;
      }
    }
    fail("Missing EditInspectionToolsSettingsAction; all emptyActions="+emptyActions);
  }
}
