// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.lambdaToExplicit;

import com.intellij.codeInspection.*;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.siyeh.ig.callMatcher.CallMatcher.instanceCall;

public class ExcessiveLambdaUsageInspection extends AbstractBaseJavaLocalInspectionTool {
  private static final CallMatcher LIST_REPLACE_ALL =
    instanceCall(CommonClassNames.JAVA_UTIL_LIST, "replaceAll").parameterTypes("java.util.function.UnaryOperator");

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
        if (ContainerUtil.or(lambda.getParameterList().getParameters(),
                             param -> ExpressionUtils.isReferenceTo(expr, param))) {
          return;
        }
        if (LIST_REPLACE_ALL.test(call)) {
          registerProblem(lambda, expr, new ReplaceWithCollectionsFillFix());
          return;
        }
        for (LambdaAndExplicitMethodPair info : LambdaAndExplicitMethodPair.INFOS) {
          if (info.isLambdaCall(call, lambda)) {
            registerProblem(lambda, expr, new RemoveExcessiveLambdaFix(info, info.getExplicitMethodName(call)));
          }
        }
      }

      private void registerProblem(PsiLambdaExpression lambda, PsiExpression expr, LocalQuickFix fix) {
        holder.registerProblem(lambda, JavaBundle.message("inspection.excessive.lambda.message"),
                               ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                               new TextRange(0, expr.getStartOffsetInParent()),
                               fix);
      }
    };
  }

  static class RemoveExcessiveLambdaFix implements LocalQuickFix {
    private final LambdaAndExplicitMethodPair myInfo;
    private final String myName;

    RemoveExcessiveLambdaFix(LambdaAndExplicitMethodPair info, String name) {
      myInfo = info;
      myName = name;
    }

    @Nls
    @NotNull
    @Override
    public String getName() {
      return JavaBundle.message("inspection.excessive.lambda.fix.name", myName);
    }

    @Nls
    @NotNull
    @Override
    public String getFamilyName() {
      return JavaBundle.message("inspection.excessive.lambda.fix.family.name");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Context context = Context.from(descriptor.getStartElement());
      if (context == null) return;
      ExpressionUtils.bindCallTo(context.myCall, myInfo.getExplicitMethodName(context.myCall));
      CommentTracker ct = new CommentTracker();
      ct.replaceAndRestoreComments(context.myLambda, ct.text(context.myBody));
    }
  }

  static class ReplaceWithCollectionsFillFix implements LocalQuickFix {
    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.excessive.lambda.fix.name", "Collections.fill()");
    }

    @Override
    public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      Context context = Context.from(descriptor.getStartElement());
      if (context == null) return;
      PsiExpression expression = ExpressionUtils.getEffectiveQualifier(context.myCall.getMethodExpression());
      if (expression == null) return;
      CommentTracker ct = new CommentTracker();
      String firstArg = expression instanceof PsiSuperExpression ? "this" : ct.text(expression);
      PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
      String text = "java.util.Collections.fill(" + firstArg + ", " + ct.text(context.myBody) + ")";
      PsiExpression replacement = factory.createExpressionFromText(text, context.myCall);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(replacement);
      ct.replaceAndRestoreComments(context.myCall, replacement);
    }
  }

  private static class Context {
    private @NotNull final PsiMethodCallExpression myCall;
    private @NotNull final PsiLambdaExpression myLambda;
    private @NotNull final PsiElement myBody;

    private Context(@NotNull PsiMethodCallExpression call,
                    @NotNull PsiLambdaExpression lambda,
                    @NotNull PsiElement body) {
      myCall = call;
      myLambda = lambda;
      myBody = body;
    }

    @Nullable
    static Context from(@Nullable PsiElement element) {
      if (!(element instanceof PsiLambdaExpression)) return null;
      PsiLambdaExpression lambda = (PsiLambdaExpression)element;
      PsiElement body = lambda.getBody();
      if (body == null) return null;
      PsiMethodCallExpression call = PsiTreeUtil.getParentOfType(lambda, PsiMethodCallExpression.class);
      if (call == null) return null;
      return new Context(call, lambda, body);
    }
  }
}
