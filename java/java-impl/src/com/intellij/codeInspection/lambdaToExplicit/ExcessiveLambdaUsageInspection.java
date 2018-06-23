// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambdaToExplicit;

import com.intellij.codeInspection.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

public class ExcessiveLambdaUsageInspection extends AbstractBaseJavaLocalInspectionTool {

  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    if (!PsiUtil.isLanguageLevel8OrHigher(holder.getFile())) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new JavaElementVisitor() {
      @Override
      public void visitLambdaExpression(PsiLambdaExpression lambda) {
        PsiElement parent = lambda.getParent();
        if (!(parent instanceof PsiExpressionList)) return;
        PsiMethodCallExpression call = ObjectUtils.tryCast(parent.getParent(), PsiMethodCallExpression.class);
        if (call == null) return;
        if (!(lambda.getBody() instanceof PsiExpression)) return;
        PsiExpression expr = (PsiExpression)lambda.getBody();
        if (!ExpressionUtils.isSafelyRecomputableExpression(expr)) return;
        if (Stream.of(lambda.getParameterList().getParameters()).anyMatch(param -> ExpressionUtils.isReferenceTo(expr, param))) return;

        for (LambdaAndExplicitMethodPair info : LambdaAndExplicitMethodPair.INFOS) {
          if(info.isLambdaCall(call, lambda)) {
            holder.registerProblem(lambda, InspectionsBundle.message("inspection.excessive.lambda.message"),
                                   ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                                   new TextRange(0, expr.getStartOffsetInParent()),
                                   new RemoveExcessiveLambdaFix(info, info.getExplicitMethodName(call)));
          }
        }
      }
    };
  }

  static class RemoveExcessiveLambdaFix implements LocalQuickFix {
    private final LambdaAndExplicitMethodPair myInfo;
    private final String myName;

    public RemoveExcessiveLambdaFix(LambdaAndExplicitMethodPair info, String name) {
      myInfo = info;
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return InspectionsBundle.message("inspection.excessive.lambda.fix.name", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionsBundle.message("inspection.excessive.lambda.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      PsiElement element = descriptor.getStartElement();
      if(!(element instanceof PsiLambdaExpression)) return;
      PsiLambdaExpression lambda = (PsiLambdaExpression)element;
      PsiElement body = lambda.getBody();
      if(body == null) return;
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
      if(call == null) return;

      ExpressionUtils.bindCallTo(call, myInfo.getExplicitMethodName(call));
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(lambda, ct.text(body));
    }
  }
}
