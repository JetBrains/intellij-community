// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.stream.Collectors;

public final class MigrateFromJavaLangIoInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher IO_PRINT =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.lang.IO", "println").parameterCount(0),
      CallMatcher.staticCall("java.lang.IO", "println", "print").parameterCount(1)
    );

  private static final Set<String> IO_PRINT_NAMES = IO_PRINT.names().collect(Collectors.toSet());

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {

      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        String referenceName = expression.getMethodExpression().getReferenceName();
        if (referenceName == null) return;
        if (!isIOPrint(expression)) return;

        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        holder.registerProblem(methodExpression,
                               JavaBundle.message("inspection.migrate.from.java.lang.io.name"),
                               new ConvertIOToSystemOutFix(referenceName));
      }
    };
  }

  private static class ConvertIOToSystemOutFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    private final String methodName;

    private ConvertIOToSystemOutFix(@NotNull String name) { methodName = name; }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.migrate.from.java.lang.io.fix.family");
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.migrate.from.java.lang.io.fix.name", "System.out." + methodName + "()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCall)) return;
      replaceToSystemOut(methodCall);
    }
  }

  static void replaceToSystemOut(@NotNull PsiMethodCallExpression methodCall) {
    PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
    String methodName = methodExpr.getReferenceName();
    if (methodName == null) return;
    PsiExpression[] arguments = methodCall.getArgumentList().getExpressions();
    StringBuilder replacement = new StringBuilder("System.out.").append(methodName).append("(");
    if (arguments.length == 1) {
      replacement.append(arguments[0].getText());
    }
    replacement.append(')');
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(methodCall.getProject());
    PsiExpression expr = factory.createExpressionFromText(replacement.toString(), methodCall);
    new CommentTracker().replace(methodCall, expr);
  }

  private static boolean isIOPrint(@NotNull PsiMethodCallExpression expression) {
    boolean isResolvedIO = IO_PRINT.test(expression);
    if (isResolvedIO) return true;
    String name = expression.getMethodExpression().getReferenceName();
    if (!IO_PRINT_NAMES.contains(name)) return false;
    PsiExpression[] args = expression.getArgumentList().getExpressions();
    if (!(args.length == 0 || args.length == 1)) return false;
    PsiMethod method = expression.resolveMethod();
    if (method != null) return false;
    PsiExpression qualifierExpression = expression.getMethodExpression().getQualifierExpression();
    if (!(qualifierExpression instanceof PsiReferenceExpression qualifierRefExpression)) return false;
    if (qualifierRefExpression.getQualifierExpression() != null) return false;
    String referenceName = qualifierRefExpression.getReferenceName();
    if (!"IO".equals(referenceName)) return false;
    PsiElement resolvedQualifier = qualifierRefExpression.resolve();
    if (resolvedQualifier != null) return false;
    return true;
  }
}
