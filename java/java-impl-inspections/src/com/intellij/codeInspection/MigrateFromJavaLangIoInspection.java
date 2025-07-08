// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

public final class MigrateFromJavaLangIoInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher IO_PRINT =
    CallMatcher.anyOf(
      CallMatcher.staticCall("java.lang.IO", "println")
        .parameterCount(0)
        .allowUnresolved(),
      CallMatcher.staticCall("java.lang.IO", "println", "print")
        .parameterCount(1)
        .allowUnresolved()
    );

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
    PsiElement replaced = new CommentTracker().replaceAndRestoreComments(methodExpr, "java.lang.System.out." + methodName);
    if (replaced instanceof PsiReferenceExpression replacedReferenceExpression) {
      JavaCodeStyleManager.getInstance(replacedReferenceExpression.getProject()).shortenClassReferences(replacedReferenceExpression);
    }
  }

  private static boolean isIOPrint(@NotNull PsiMethodCallExpression expression) {
    if (!IO_PRINT.test(expression)) return false;
    return MigrateToJavaLangIoInspection.callIOAndSystemIdentical(expression.getArgumentList());
  }
}
