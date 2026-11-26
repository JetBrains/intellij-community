// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture;
import com.intellij.codeInsight.daemon.LightDaemonAnalyzerOptions;
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
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.codeInsight.daemon.LightDaemonAnalyzerFixture.lightDaemonAnalyzerFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
public class EmptyIntentionInspectionQuickFixTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightDaemonAnalyzerFixture> daemonFixture = lightDaemonAnalyzerFixture(
    projectFixture, () -> configureLocalInspectionTools(),
    LightDaemonAnalyzerOptions.DEFAULT.withBasePath("/java/java-tests/testData/codeInsight/daemonCodeAnalyzer/quickFix/emptyIntention"));

  private static List<LocalInspectionTool> configureLocalInspectionTools() {
    return List.of(new DefUseInspection(), new LocalInspectionTool() {
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
    });
  }

  @Test
  public void testX() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile("/X.java");
    List<IntentionAction> emptyActions = ContainerUtil.filter(daemon.getAvailableActions(),
      action ->
        IntentionActionDelegate.unwrap(action) instanceof EmptyIntentionAction);
    assertEquals(1, emptyActions.size(), emptyActions.toString());
  }

  @Test
  public void testLowPriority() {
    LightDaemonAnalyzerFixture daemon = daemonFixture.get();
    daemon.configureByFile("/LowPriority.java");
    List<IntentionAction> emptyActions = daemon.getAvailableActions();
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
