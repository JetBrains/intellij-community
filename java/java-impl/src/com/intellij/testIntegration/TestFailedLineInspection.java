// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testIntegration;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class TestFailedLineInspection extends LocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {

        TestStateStorage.Record state = TestFailedLineManager.getInstance(call.getProject()).getFailedLineState(call);
        if (state == null) return;

        holder.registerProblem(call, state.errorMessage, new RunActionFix(call));
      }
    };
  }

  private static class RunActionFix implements LocalQuickFix, Iconable {
    private final ConfigurationContext myContext;
    private final Executor myExecutor;
    private final RunnerAndConfigurationSettings myConfiguration;

    public RunActionFix(PsiElement element) {
      myExecutor = ExecutorRegistry.getInstance().getExecutorById(DefaultRunExecutor.EXECUTOR_ID);
      myContext = new ConfigurationContext(element);
      myConfiguration = myContext.getConfiguration();
    }

    @Nls(capitalization = Nls.Capitalization.Sentence)
    @NotNull
    @Override
    public String getFamilyName() {
      String text = myExecutor.getStartActionText(ProgramRunnerUtil.shortenName(myConfiguration.getName(), 0));
      return UIUtil.removeMnemonic(text);
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      ExecutionUtil.runConfiguration(myConfiguration, myExecutor);
    }

    @Override
    public Icon getIcon(int flags) {
      return myExecutor.getIcon();
    }
  }
}
