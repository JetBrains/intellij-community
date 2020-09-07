// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class RedundantFileCreationInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitNewExpression(PsiNewExpression newExpression) {
        super.visitNewExpression(newExpression);

        final String[] targetTypes = new String[] {
          "java.io.FileInputStream", "java.io.FileOutputStream", "java.io.FileReader","java.io.FileWriter",
          "java.io.PrintStream", "java.io.PrintWriter", "java.util.Formatter"
        };

        final PsiType type = newExpression.getType();
        if (!TypeUtils.typeEquals(type, targetTypes)) {
          return;
        }

        final PsiMethod streamConstructor = newExpression.resolveConstructor();
        if (streamConstructor == null) return;
        final PsiParameter[] streamParams = streamConstructor.getParameterList().getParameters();
        if (streamParams.length != 1) return;

        if (!TypeUtils.typeEquals("java.io.File", streamParams[0].getType())) return;

        final PsiExpressionList streamArgList = newExpression.getArgumentList();
        if (streamArgList == null) return;

        final PsiExpression[] streamArgs = streamArgList.getExpressions();
        if (streamArgs.length != 1) return;

        PsiExpression streamArg = streamArgs[0];
        if (!(streamArg instanceof PsiNewExpression)) return;

        final PsiMethod fileConstructor = ((PsiNewExpression)streamArg).resolveConstructor();
        if (fileConstructor == null) return;

        final PsiParameter[] fileParams = fileConstructor.getParameterList().getParameters();
        if (fileParams.length != 1) return;

        if (!TypeUtils.isJavaLangString(fileParams[0].getType())) return;

        PsiExpressionList fileArgList = ((PsiNewExpression)streamArg).getArgumentList();
        if (fileArgList == null) return;

        holder.registerProblem(streamArg,
                               JavaBundle.message("inspection.redundant.file.creation.description"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                               new TextRange(0, fileArgList.getStartOffsetInParent()),
                               new DeleteRedundantFileCreationFix());

      }
    };
  }

  private static class DeleteRedundantFileCreationFix implements LocalQuickFix {
    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.redundant.file.creation.quickfix");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiElement element = descriptor.getPsiElement();
      assert element instanceof PsiNewExpression;

      final PsiNewExpression newExpression = (PsiNewExpression)descriptor.getPsiElement();
      final PsiExpressionList argList = newExpression.getArgumentList();
      assert argList != null;

      final PsiExpression[] args = argList.getExpressions();
      assert args.length == 1;

      CommentTracker commentTracker = new CommentTracker();
      final String argText = commentTracker.text(args[0]);

      PsiReplacementUtil.replaceExpression(newExpression, argText, commentTracker);
    }
  }
}
