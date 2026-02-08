// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
        ConvertIOToSystemOutFix fix = new ConvertIOToSystemOutFix(expression, referenceName);
        holder.problem(methodExpression, JavaBundle.message("inspection.migrate.from.java.lang.io.name"))
          .fix(fix)
          .register();
      }
    };
  }

  public static @Nullable ModCommandAction createCanBeIOFix(@NotNull PsiElement psi) {
    if (!(psi instanceof PsiReferenceExpression)) return null;
    if (!(psi.getParent() instanceof PsiReferenceExpression parentReference)) return null;
    if (!(parentReference.getParent() instanceof PsiMethodCallExpression methodCallExpression)) return null;
    if (!canBeIOPrint(methodCallExpression)) return null;
    String referenceName = methodCallExpression.getMethodExpression().getReferenceName();
    if (referenceName == null) return null;
    return new MigrateFromJavaLangIoInspection.ConvertIOToSystemOutFix(methodCallExpression, referenceName)
      .withPresentation(presentation -> presentation.withPriority(PriorityAction.Priority.HIGH));
  }

  public static class ConvertIOToSystemOutFix extends PsiUpdateModCommandAction<PsiMethodCallExpression> {

    @NotNull
    private final String methodName;

    private ConvertIOToSystemOutFix(@NotNull PsiMethodCallExpression expression,
                                    @NotNull String name) {
      super(expression);
      methodName = name;
    }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.migrate.from.java.lang.io.fix.family");
    }

    @Override
    protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiMethodCallExpression element) {
      return Presentation.of(JavaBundle.message("inspection.migrate.from.java.lang.io.fix.name", "System.out." + methodName + "()"));
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

  private static boolean isIOPrint(@NotNull PsiMethodCallExpression expression) {
    if (!IO_PRINT.test(expression)) return false;
    return MigrateToJavaLangIoInspection.callIOAndSystemIdentical(expression.getArgumentList());
  }

  private static boolean canBeIOPrint(@NotNull PsiMethodCallExpression expression) {
    if (!CAN_BE_IO_PRINT.test(expression)) return false;
    return MigrateToJavaLangIoInspection.callIOAndSystemIdentical(expression.getArgumentList());
  }
}
