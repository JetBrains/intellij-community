// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.dataFlow.CommonDataflow;
import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class SlowAbstractSetRemoveAllInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final String FOR_EACH_METHOD = "forEach";

  private static final CallMatcher SET_REMOVE_ALL =
    instanceCall(CommonClassNames.JAVA_UTIL_SET, "removeAll").parameterTypes(CommonClassNames.JAVA_UTIL_COLLECTION);

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(PsiMethodCallExpression call) {
        super.visitMethodCallExpression(call);
        if (!SET_REMOVE_ALL.test(call)) return;
        final PsiExpression arg = call.getArgumentList().getExpressions()[0];
        if (!TypeUtils.expressionHasTypeOrSubtype(arg, CommonClassNames.JAVA_UTIL_LIST)) return;
        final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
        if (qualifier == null) return;
        final String s = arg.getText() + ".forEach(" + qualifier.getText() + "::remove)";
        holder.registerProblem(call,
                               JavaBundle.message("inspection.slow.abstract.set.remove.all.description"),
                               ProblemHighlightType.WARNING,
                               new ReplaceWithListForEachFix(s));
      }
    };
  }

  private static class ReplaceWithListForEachFix implements LocalQuickFix {
    final String myExpressionText;

    ReplaceWithListForEachFix(String string) {
      myExpressionText = string;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", myExpressionText);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.slow.abstract.set.remove.all.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiMethodCallExpression call = ObjectUtils.tryCast(descriptor.getPsiElement(), PsiMethodCallExpression.class);
      if (call == null) return;
      final PsiExpression[] args = call.getArgumentList().getExpressions();
      if (args.length != 1) return;
      final PsiExpression arg = args[0];
      final PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
      if (qualifier == null) return;
      ExpressionUtils.bindCallTo(call, FOR_EACH_METHOD);
      final CommentTracker ct = new CommentTracker();
      final String setRemove = qualifier.getText() + "::remove";
      ct.replace(qualifier, arg);
      ct.replace(arg, setRemove);
    }
  }
}
