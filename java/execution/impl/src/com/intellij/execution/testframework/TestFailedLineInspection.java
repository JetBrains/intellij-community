// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework;

import com.intellij.codeInspection.*;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.execution.*;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.runners.ExecutionUtil;
import com.intellij.execution.stacktrace.StackTraceLine;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.*;
import com.intellij.testIntegration.TestFailedLineManager;
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

        PsiElement nameElement = call.getMethodExpression().getReferenceNameElement();
        if (nameElement == null) return;

        TestStateStorage.Record state = TestFailedLineManager.getInstance(call.getProject()).getFailedLineState(call);
        if (state == null) return;

        LocalQuickFix[] fixes = {new DebugFailedTestFix(call, state.topStacktraceLine),
          new RunActionFix(call, DefaultRunExecutor.EXECUTOR_ID)};
        ProblemDescriptor descriptor = InspectionManager.getInstance(call.getProject())
                                                        .createProblemDescriptor(nameElement, state.errorMessage, isOnTheFly, fixes,
                                                                                 ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
        descriptor.setTextAttributes(CodeInsightColors.RUNTIME_ERROR);
        holder.registerProblem(descriptor);
      }
    };
  }

  private static class DebugFailedTestFix extends RunActionFix{

    private final String myTopStacktraceLine;

    public DebugFailedTestFix(PsiElement element, String topStacktraceLine) {
      super(element, DefaultDebugExecutor.EXECUTOR_ID);
      myTopStacktraceLine = topStacktraceLine;
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      StackTraceLine line = new StackTraceLine(project, myTopStacktraceLine);
      Location<PsiMethod> location = line.getMethodLocation(project);
      if (location != null) {
        Document document = PsiDocumentManager.getInstance(project).getDocument(location.getPsiElement().getContainingFile());
        if (document != null) {
          DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().addLineBreakpoint(document, line.getLineNumber());
        }
      }
      super.applyFix(project, descriptor);
    }
  }

  private static class RunActionFix implements LocalQuickFix, Iconable {
    private final ConfigurationContext myContext;
    private final Executor myExecutor;
    private final RunnerAndConfigurationSettings myConfiguration;

    public RunActionFix(PsiElement element, String executorId) {
      myExecutor = ExecutorRegistry.getInstance().getExecutorById(executorId);
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
