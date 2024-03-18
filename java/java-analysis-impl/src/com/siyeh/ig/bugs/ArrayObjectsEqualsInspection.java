// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.bugs;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

public final class ArrayObjectsEqualsInspection extends BaseInspection {
  enum Kind {
    TO_EQUALS("equals", "array.equals.problem.descriptor"),
    TO_DEEP_EQUALS("deepEquals", "array.equals.problem.descriptor"),
    TO_DEEP_HASH_CODE("deepHashCode", "array.hashcode.problem.descriptor");

    private final String myNewMethodName;
    private final @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) String myMessage;

    Kind(String newMethodName, @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) String message) {
      myNewMethodName = newMethodName;
      myMessage = message;
    }

    @Override
    public @InspectionMessage String toString() {
      return InspectionGadgetsBundle.message(myMessage, "Arrays." + myNewMethodName + "()");
    }

    public String getNewMethodName() {
      return myNewMethodName;
    }
  }

  @NotNull
  @Override
  protected String buildErrorString(Object... infos) {
    final Kind kind = (Kind)infos[0];
    return kind.toString();
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }

  @Nullable
  @Override
  protected LocalQuickFix buildFix(Object... infos) {
    final Kind kind = (Kind)infos[0];
    return new ArrayEqualsHashCodeFix(kind);
  }

  private static class ArrayEqualsHashCodeFix extends PsiUpdateModCommandQuickFix {

    private final Kind myKind;

    ArrayEqualsHashCodeFix(Kind kind) {
      myKind = kind;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "Arrays." + myKind.myNewMethodName + "()");
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return getName();
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement startElement, @NotNull ModPsiUpdater updater) {
      final PsiElement element = startElement.getParent().getParent();
      if (!(element instanceof PsiMethodCallExpression methodCallExpression)) {
        return;
      }
      CommentTracker commentTracker = new CommentTracker();
      String newExpression = "java.util.Arrays." + myKind.getNewMethodName() +
                             commentTracker.text(methodCallExpression.getArgumentList());
      PsiReplacementUtil.replaceExpressionAndShorten(methodCallExpression,
                                                     newExpression, commentTracker);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ArrayObjectsEqualsVisitor();
  }

  private static class ArrayObjectsEqualsVisitor extends BaseInspectionVisitor {

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      final PsiReferenceExpression methodExpression = expression.getMethodExpression();
      final String methodName = methodExpression.getReferenceName();
      final PsiExpressionList argumentList = expression.getArgumentList();
      final PsiExpression[] expressions = argumentList.getExpressions();
      if (!((expressions.length == 1 && HardcodedMethodConstants.HASH_CODE.equals(methodName)) ||
            (expressions.length == 2 && HardcodedMethodConstants.EQUALS.equals(methodName)))) {
        return;
      }
      final PsiExpression argument1 = expressions[0];
      final PsiType type1 = argument1.getType();
      if (!(type1 instanceof PsiArrayType)) {
        return;
      }
      final int dimensions = type1.getArrayDimensions();
      if (expressions.length == 2) {
        final PsiExpression argument2 = expressions[1];
        final PsiType type2 = argument2.getType();
        if (!(type2 instanceof PsiArrayType)) {
          return;
        }
        if (dimensions != type2.getArrayDimensions()) {
          return;
        }
      }
      final PsiMethod method = expression.resolveMethod();
      if (method == null) {
        return;
      }
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      final Kind kind = getKind(containingClass.getQualifiedName(), methodName, dimensions);
      if (kind == null) {
        return;
      }
      registerMethodCallError(expression, kind);
    }

    private static Kind getKind(String className, String methodName, int dimensions) {
      final boolean isJavaUtilObjects = CommonClassNames.JAVA_UTIL_OBJECTS.equals(className);
      final boolean isJavaUtilArrays = CommonClassNames.JAVA_UTIL_ARRAYS.equals(className);
      final boolean isEquals = HardcodedMethodConstants.EQUALS.equals(methodName);
      final boolean isHashCode = HardcodedMethodConstants.HASH_CODE.equals(methodName);
      if (isJavaUtilObjects && isEquals) {
        return dimensions > 1 ? Kind.TO_DEEP_EQUALS : Kind.TO_EQUALS;
      } else if (isJavaUtilArrays && dimensions > 1) {
          if (isEquals) {
            return Kind.TO_DEEP_EQUALS;
          } else if (isHashCode) {
            return Kind.TO_DEEP_HASH_CODE;
          }
      }
      return null;
    }
  }
}
