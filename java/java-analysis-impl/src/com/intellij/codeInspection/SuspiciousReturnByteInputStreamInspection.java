// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import org.jetbrains.annotations.NotNull;

public class SuspiciousReturnByteInputStreamInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher READ_INPUT_STREAM = CallMatcher.anyOf(
    CallMatcher.instanceCall("java.io.InputStream", "read").parameterCount(0)
  );

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitReturnStatement(@NotNull PsiReturnStatement statement) {
        PsiExpression returnValue = statement.getReturnValue();
        if (returnValue == null) return;
        PsiType type = returnValue.getType();
        if (type == null) return;
        if (!type.equalsToText("byte") && !type.equalsToText(CommonClassNames.JAVA_LANG_BYTE)) {
          return;
        }
        process(returnValue);
      }

      private void process(PsiExpression expression) {
        PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class);
        if (method == null) return;
        if (!READ_INPUT_STREAM.methodMatches(method)) return;
        registerProblem(expression);
      }

      private void registerProblem(PsiExpression expression) {
        holder.registerProblem(expression, JavaBundle.message("inspection.suspicious.return.byte.input.stream.name"),
                               new ConvertToUnsignedByteFix());
      }
    };
  }

  private static class ConvertToUnsignedByteFix extends PsiUpdateModCommandQuickFix {

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.suspicious.return.byte.input.stream.convert.to.unsigned");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      if (!(element instanceof PsiExpression expression)) {
        return;
      }
      CommentTracker tracker = new CommentTracker();
      String text = tracker.text(expression, ParenthesesUtils.BINARY_AND_PRECEDENCE);
      tracker.replace(expression, text + "& 0xFF");
    }
  }
}