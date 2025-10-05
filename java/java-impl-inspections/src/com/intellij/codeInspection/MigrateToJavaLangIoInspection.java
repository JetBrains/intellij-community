// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInspection.options.OptPane;
import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.pom.java.JavaFeature;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.callMatcher.CallMatcher;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static com.intellij.psi.CommonClassNames.JAVA_LANG_IO;

public final class MigrateToJavaLangIoInspection extends AbstractBaseJavaLocalInspectionTool {

  public boolean processImplicitClasses = true;
  public boolean processOrdinaryClasses = true;

  private static final CallMatcher PRINT_STREAM_PRINT =
    CallMatcher.anyOf(
      CallMatcher.instanceCall("java.io.PrintStream", "println").parameterCount(0),
      CallMatcher.instanceCall("java.io.PrintStream", "println", "print").parameterCount(1)
    );


  @Override
  public @NotNull Set<@NotNull JavaFeature> requiredFeatures() {
    return Set.of(JavaFeature.JAVA_LANG_IO);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return OptPane.pane(
      OptPane.checkbox("processOrdinaryClasses",
                       JavaBundle.message("inspection.migrate.to.java.lang.io.option.ordinary")),
      OptPane.checkbox("processImplicitClasses",
                       JavaBundle.message("inspection.migrate.to.java.lang.io.option.implicit"))
    );
  }

  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JavaElementVisitor() {
      @Override
      public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
        super.visitMethodCallExpression(expression);

        String referenceName = expression.getMethodExpression().getReferenceName();
        if (referenceName == null) return;
        if (!isSystemOutPrintln(expression)) return;

        PsiClass topClass = PsiTreeUtil.getTopmostParentOfType(expression, PsiClass.class);
        if (topClass == null) return;
        boolean applicableForClass = isApplicableForClass(topClass);

        PsiReferenceExpression methodExpression = expression.getMethodExpression();
        if (applicableForClass) {
          holder.registerProblem(methodExpression,
                                 JavaBundle.message("inspection.migrate.to.java.lang.io.name"),
                                 new ConvertSystemOutPrintlnFix(referenceName));
        }
        else {
          holder.registerProblem(methodExpression,
                                 JavaBundle.message("inspection.migrate.to.java.lang.io.name"),
                                 ProblemHighlightType.INFORMATION,
                                 new ConvertSystemOutPrintlnFix(referenceName));
        }
      }

      private boolean isApplicableForClass(@Nullable PsiClass psiClass) {
        if (psiClass == null) {
          return false;
        }
        if (PsiTreeUtil.getParentOfType(psiClass, PsiClass.class, true) != null) {
          return false;
        }
        if (psiClass instanceof PsiImplicitClass) {
          return processImplicitClasses;
        }
        return processOrdinaryClasses;
      }
    };
  }

  private static class ConvertSystemOutPrintlnFix extends PsiUpdateModCommandQuickFix {

    @NotNull
    private final String methodName;

    private ConvertSystemOutPrintlnFix(@NotNull String name) { methodName = name; }

    @Override
    public @NotNull String getFamilyName() {
      return JavaBundle.message("inspection.migrate.to.java.lang.io.fix.family");
    }

    @Override
    public @NotNull String getName() {
      return JavaBundle.message("inspection.migrate.to.java.lang.io.fix.name", "IO." + methodName + "()");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      PsiElement parent = element.getParent();
      if (!(parent instanceof PsiMethodCallExpression methodCall)) return;
      replaceToIO(methodCall);
    }
  }

  static void replaceToIO(@NotNull PsiMethodCallExpression methodCall) {
    PsiReferenceExpression methodExpr = methodCall.getMethodExpression();
    String methodName = methodExpr.getReferenceName();
    if (methodName == null) return;
    PsiElement replaced = new CommentTracker().replaceAndRestoreComments(methodExpr, JAVA_LANG_IO + "." + methodName);
    if (replaced instanceof PsiReferenceExpression replacedReferenceExpression) {
      JavaCodeStyleManager.getInstance(replacedReferenceExpression.getProject()).shortenClassReferences(replacedReferenceExpression);
    }
  }

  static boolean isSystemOutPrintln(@NotNull PsiMethodCallExpression expression) {
    if (!PRINT_STREAM_PRINT.test(expression)) return false;
    if (!isSystemOutCall(expression)) return false;
    return callIOAndSystemIdentical(expression.getArgumentList());
  }

  private static boolean isSystemOutCall(@NotNull PsiMethodCallExpression expression) {
    PsiReferenceExpression methodExpression = expression.getMethodExpression();
    PsiExpression qualifier = methodExpression.getQualifierExpression();
    if (!(qualifier instanceof PsiReferenceExpression ref)) return false;
    PsiElement resolved = ref.resolve();
    if (!(resolved instanceof PsiField field)) return false;
    if (!field.getName().equals("out")) return false;
    PsiClass containingClass = field.getContainingClass();
    if (containingClass == null) return false;
    if (!CommonClassNames.JAVA_LANG_SYSTEM.equals(containingClass.getQualifiedName())) return false;
    return true;
  }

  static boolean callIOAndSystemIdentical(@NotNull PsiExpressionList list) {
    PsiExpression[] expressions = list.getExpressions();
    if (expressions.length == 0) return true;
    if (expressions.length == 1) {
      PsiType type = expressions[0].getType();
      if (type == null) return false;
      if (type instanceof PsiArrayType arrayType && PsiTypes.charType().equals(arrayType.getComponentType())) return false;
    }
    return true;
  }
}
