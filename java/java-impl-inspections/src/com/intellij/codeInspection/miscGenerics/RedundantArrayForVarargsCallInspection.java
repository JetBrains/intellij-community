// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.siyeh.ig.callMatcher.CallMatcher.exactInstanceCall;

public final class RedundantArrayForVarargsCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, final boolean isOnTheFly) {
    return new RedundantArrayForVarargVisitor(holder);
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return InspectionsBundle.message("group.names.verbose.or.redundant.code.constructs");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "RedundantArrayCreation";
  }

  private static final class RedundantArrayForVarargVisitor extends JavaElementVisitor {

    private static final String[] LOGGER_NAMES = new String[]{"debug", "error", "info", "trace", "warn"};

    private static final CallMatcher LOGGER_MESSAGE_CALL = exactInstanceCall("org.slf4j.Logger", LOGGER_NAMES)
      .parameterTypes(String.class.getName(), "java.lang.Object...");

    private static final LocalQuickFix redundantArrayForVarargsCallFixAction = new RedundantArrayForVarargsCallFix();

    private @NotNull final ProblemsHolder myHolder;

    private RedundantArrayForVarargVisitor(@NotNull final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitCallExpression(@NotNull PsiCallExpression expression) {
      super.visitCallExpression(expression);
      checkCall(expression);
    }

    @Override
    public void visitEnumConstant(@NotNull PsiEnumConstant expression) {
      super.visitEnumConstant(expression);
      checkCall(expression);
    }

    private void checkCall(PsiCall expression) {
      PsiExpression[] initializers = CommonJavaRefactoringUtil.getArrayInitializersToFlattenInVarargs(expression);
      if (initializers == null) return;
      if (!(expression instanceof PsiExpression && LOGGER_MESSAGE_CALL.matches((PsiExpression)expression)) && 
          !CommonJavaRefactoringUtil.isSafeToFlattenToVarargsCall(expression, initializers)) {
        return;
      }
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] args = Objects.requireNonNull(argumentList).getExpressions();
      PsiExpression arrayCreation = Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(args[args.length - 1]));
      if (!(arrayCreation instanceof PsiNewExpression)) return;
      final String message = JavaBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor");
      PsiArrayInitializerExpression arrayInitializer = ((PsiNewExpression)arrayCreation).getArrayInitializer();
      if (arrayInitializer == null) {
        myHolder.registerProblem(
          arrayCreation,
          message,
          redundantArrayForVarargsCallFixAction);
      } else {
        myHolder.registerProblem(
          arrayCreation,
          new TextRange(0, arrayInitializer.getStartOffsetInParent()),
          message,
          redundantArrayForVarargsCallFixAction);
      }
    }


    private static final class RedundantArrayForVarargsCallFix extends PsiUpdateModCommandQuickFix {
      @Override
      protected void applyFix(@NotNull Project project, @NotNull PsiElement arrayCreation, @NotNull ModPsiUpdater updater) {
        if (!(arrayCreation instanceof PsiNewExpression newExpression)) return;
        CommonJavaRefactoringUtil.inlineArrayCreationForVarargs(newExpression);
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return JavaBundle.message("inspection.redundant.array.creation.quickfix");
      }
    }
  }
}
