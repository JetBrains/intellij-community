// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandService;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_IO;

public final class MigrateFromJavaLangIoInspection extends AbstractBaseJavaLocalInspectionTool {

  private static final CallMatcher CAN_BE_IO_PRINT =
    CallMatcher.anyOf(CallMatcher.staticCall(JAVA_LANG_IO, "println")
                        .parameterCount(0)
                        .allowStaticUnresolved(),
                      CallMatcher.staticCall(JAVA_LANG_IO, "println", "print")
                        .parameterCount(1)
                        .allowStaticUnresolved());

  private static final CallMatcher IO_PRINT =
    CallMatcher.anyOf(CallMatcher.staticCall(JAVA_LANG_IO, "println")
                        .parameterCount(0),
                      CallMatcher.staticCall(JAVA_LANG_IO, "println", "print")
                        .parameterCount(1));

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
        ConvertIOToSystemOutFix fix = new ConvertIOToSystemOutFix(expression);
        LocalQuickFix localQuickFix = ModCommandService.getInstance().wrapToQuickFix(fix);
        holder.registerProblem(methodExpression, JavaBundle.message("inspection.migrate.from.java.lang.io.name"), localQuickFix);
      }
    };
  }

  public static class ConvertIOToSystemOutFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {

    public ConvertIOToSystemOutFix(@NotNull PsiMethodCallExpression expression) {
      super(expression);
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.migrate.from.java.lang.io.fix.family");
    }

    @Override
    protected void invoke(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element, @NotNull ModPsiUpdater updater) {
      replaceToSystemOut(element);
    }
  }

  private static void replaceToSystemOut(@NotNull PsiMethodCallExpression methodCall) {
    PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
    String methodName = methodExpr.getReferenceName();
    if (methodName == null) return;
    PsiElement replaced = new CommentTracker().replaceAndRestoreComments(methodExpr, "java.lang.System.out." + methodName);
    if (replaced instanceof PsiReferenceExpression replacedReferenceExpression) {
      JavaCodeStyleManager.getInstance(replacedReferenceExpression.getProject()).shortenClassReferences(replacedReferenceExpression);
    }
  }

  public static boolean isIOPrint(@NotNull PsiMethodCallExpression expression) {
    if (!IO_PRINT.test(expression)) return false;
    return MigrateToJavaLangIoInspection.callIOAndSystemIdentical(expression.getArgumentList());
  }

  public static boolean canBeIOPrint(@NotNull PsiMethodCallExpression expression) {
    if (!CAN_BE_IO_PRINT.test(expression)) return false;
    return MigrateToJavaLangIoInspection.callIOAndSystemIdentical(expression.getArgumentList());
  }
}
