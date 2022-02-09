// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.miscGenerics;

import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

import static com.siyeh.ig.callMatcher.CallMatcher.exactInstanceCall;

/**
 * @author ven
 */
public class RedundantArrayForVarargsCallInspection extends AbstractBaseJavaLocalInspectionTool implements CleanupLocalInspectionTool {
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
    private static final Logger LOG = Logger.getInstance(RedundantArrayForVarargVisitor.class);

    private static final String[] LOGGER_NAMES = new String[]{"debug", "error", "info", "trace", "warn"};

    private static final CallMatcher LOGGER_MESSAGE_CALL = exactInstanceCall("org.slf4j.Logger", LOGGER_NAMES)
      .parameterTypes(String.class.getName(), "java.lang.Object...");

    private static final LocalQuickFix myQuickFixAction = new MyQuickFix();

    private @NotNull final ProblemsHolder myHolder;

    private RedundantArrayForVarargVisitor(@NotNull final ProblemsHolder holder) {
      myHolder = holder;
    }

    @Override
    public void visitCallExpression(PsiCallExpression expression) {
      super.visitCallExpression(expression);
      checkCall(expression);
    }

    @Override
    public void visitEnumConstant(PsiEnumConstant expression) {
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
      final String message = JavaBundle.message("inspection.redundant.array.creation.for.varargs.call.descriptor");
      PsiExpressionList argumentList = expression.getArgumentList();
      PsiExpression[] args = Objects.requireNonNull(argumentList).getExpressions();
      myHolder.registerProblem(Objects.requireNonNull(PsiUtil.skipParenthesizedExprDown(args[args.length - 1])), message, myQuickFixAction);
    }


    private static final class MyQuickFix implements LocalQuickFix {
      @Override
      public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
        PsiNewExpression arrayCreation = (PsiNewExpression)descriptor.getPsiElement();
        if (arrayCreation == null) return;
        CommonJavaRefactoringUtil.inlineArrayCreationForVarargs(arrayCreation);
      }

      @Override
      @NotNull
      public String getFamilyName() {
        return JavaBundle.message("inspection.redundant.array.creation.quickfix");
      }
    }
  }
}
