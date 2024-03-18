// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class ArrayHashCodeInspection extends BaseInspection {
  enum Kind {
    ARRAY_HASH_CODE,
    OBJECTS_HASH
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    return switch ((Kind)infos[1]) {
      case ARRAY_HASH_CODE -> InspectionGadgetsBundle.message("array.hash.code.problem.descriptor");
      case OBJECTS_HASH -> InspectionGadgetsBundle.message("objects.hash.problem.descriptor");
    };
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final PsiArrayType type = (PsiArrayType)infos[0];
    final boolean deepHashCode = type.getComponentType() instanceof PsiArrayType;
    return switch ((Kind)infos[1]) {
      case ARRAY_HASH_CODE -> new ArrayHashCodeFix(deepHashCode);
      case OBJECTS_HASH -> new ObjectsHashFix(deepHashCode);
    };
  }

  private static class ArrayHashCodeFix extends PsiUpdateModCommandQuickFix {

    private final boolean deepHashCode;

    ArrayHashCodeFix(boolean deepHashCode) {
      this.deepHashCode = deepHashCode;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x.call", deepHashCode ? "Arrays.deepHashCode()" : "Arrays.hashCode()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("array.hash.code.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiElement parent = element.getParent();
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      if (qualifier == null) {
        return;
      }
      @NonNls final StringBuilder newExpressionText = new StringBuilder();
      if (deepHashCode) {
        newExpressionText.append("java.util.Arrays.deepHashCode(");
      }
      else {
        newExpressionText.append("java.util.Arrays.hashCode(");
      }
      CommentTracker commentTracker = new CommentTracker();
      newExpressionText.append(commentTracker.text(qualifier));
      newExpressionText.append(')');
      PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression, newExpressionText.toString(), commentTracker);
    }
  }

  private static class ObjectsHashFix extends PsiUpdateModCommandQuickFix {
    private final boolean deepHashCode;

    ObjectsHashFix(boolean deepHashCode) {
      this.deepHashCode = deepHashCode;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message("wrap.with.arrays.hash.code.quickfix", deepHashCode ? "Arrays.deepHashCode()" : "Arrays.hashCode()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("objects.hash.fix.family.name");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      CommentTracker tracker = new CommentTracker();
      String text =
        (deepHashCode ? "java.util.Arrays.deepHashCode(" : "java.util.Arrays.hashCode(") + tracker.text(element) + ')';
      PsiElement result = tracker.replaceAndRestoreComments(element, text);
      JavaCodeStyleManager.getInstance(result.getProject()).shortenClassReferences(result);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayHashCodeVisitor();
  }

  private static class ArrayHashCodeVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      final PsiExpressionList argumentList = expression.getArgumentList();
      if (HardcodedMethodConstants.HASH_CODE.equals(methodName)) {
        if (!argumentList.isEmpty()) return;
        final PsiExpression qualifier = methodExpression.getQualifierExpression();
        if (qualifier == null) return;
        final PsiType type = qualifier.getType();
        if (!(type instanceof PsiArrayType)) return;
        registerMethodCallError(expression, type, Kind.ARRAY_HASH_CODE);
      } else if ("hash".equals(methodName)) {
        if (argumentList.getExpressionCount() == 1) return;
        final PsiMethod method = expression.resolveMethod();
        if (method == null) {
          return;
        }
        final PsiClass containingClass = method.getContainingClass();
        if (containingClass == null || !CommonClassNames.JAVA_UTIL_OBJECTS.equals(containingClass.getQualifiedName())) {
          return;
        }
        final PsiExpression[] expressions = argumentList.getExpressions();
        for (PsiExpression arg : expressions) {
          final PsiType type = arg.getType();
          if (!(type instanceof PsiArrayType)) continue;
          registerError(arg, type, Kind.OBJECTS_HASH);
        }
      }
    }
  }
}
