/*
 * Copyright 2003-2018 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.jdk;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.CommentTracker;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodCallUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class AutoBoxingInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAddedToCollection = false;

  @NonNls static final Map<String, String> s_boxingClasses = new HashMap<>(8);

  static {
    s_boxingClasses.put("byte", CommonClassNames.JAVA_LANG_BYTE);
    s_boxingClasses.put("short", CommonClassNames.JAVA_LANG_SHORT);
    s_boxingClasses.put("int", CommonClassNames.JAVA_LANG_INTEGER);
    s_boxingClasses.put("long", CommonClassNames.JAVA_LANG_LONG);
    s_boxingClasses.put("float", CommonClassNames.JAVA_LANG_FLOAT);
    s_boxingClasses.put("double", CommonClassNames.JAVA_LANG_DOUBLE);
    s_boxingClasses.put("boolean", CommonClassNames.JAVA_LANG_BOOLEAN);
    s_boxingClasses.put("char", CommonClassNames.JAVA_LANG_CHARACTER);
  }

  @Override
  public String getAlternativeID() {
    return "boxing";
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("auto.boxing.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAddedToCollection", InspectionGadgetsBundle.message("auto.boxing.ignore.added.to.collection.option")));
  }

  @Override
  public boolean shouldInspect(@NotNull PsiFile file) {
    return PsiUtil.isLanguageLevel5OrHigher(file);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new AutoBoxingVisitor();
  }

  @Override
  public LocalQuickFix buildFix(Object... infos) {
    if (infos.length == 0) {
      return null;
    }
    return new AutoBoxingFix();
  }

  /**
   * Replaces expression of primitive type with explicitly boxed expression
   * @param expression expression to box
   */
  public static void replaceWithBoxing(@NotNull PsiExpression expression) {
    final PsiType type = expression.getType();
    if (!(type instanceof PsiPrimitiveType)) return;
    final PsiType expectedType = ((PsiPrimitiveType)type).getBoxedType(expression);
    if (expectedType == null) {
      return;
    }
    final String expectedTypeText = expectedType.getCanonicalText();
    final String classToConstruct;
    if (s_boxingClasses.containsValue(expectedTypeText)) {
      classToConstruct = expectedTypeText;
    }
    else {
      final String expressionTypeText = type.getCanonicalText();
      classToConstruct = s_boxingClasses.get(expressionTypeText);
    }
    if (shortcutReplace(expression, classToConstruct)) {
      return;
    }
    final PsiExpression strippedExpression = PsiUtil.skipParenthesizedExprDown(expression);
    if (strippedExpression == null) {
      return;
    }
    CommentTracker commentTracker = new CommentTracker();
    @NonNls final String expressionText = strippedExpression.getText();
    @NonNls final String newExpression;
    if ("true".equals(expressionText)) {
      newExpression = "java.lang.Boolean.TRUE";
    }
    else if ("false".equals(expressionText)) {
      newExpression = "java.lang.Boolean.FALSE";
    }
    else {
      commentTracker.markUnchanged(strippedExpression);
      newExpression = classToConstruct + ".valueOf(" + expressionText + ')';
    }
    final PsiElement parent = expression.getParent();
    if (parent instanceof PsiTypeCastExpression typeCastExpression) {
      PsiReplacementUtil.replaceExpression(typeCastExpression, newExpression, commentTracker);
    } else {
      PsiReplacementUtil.replaceExpression(expression, newExpression, commentTracker);
    }
  }

  private static boolean shortcutReplace(PsiExpression expression, String classToConstruct) {
    if (!(expression instanceof PsiMethodCallExpression methodCallExpression)) {
      return false;
    }
    final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
    final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
    if (qualifierExpression == null) {
      return false;
    }
    boolean shouldReplace = switch (classToConstruct) {
      case CommonClassNames.JAVA_LANG_INTEGER ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_INTEGER, PsiTypes.intType(), "intValue");
      case CommonClassNames.JAVA_LANG_SHORT ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_SHORT, PsiTypes.shortType(), "shortValue");
      case CommonClassNames.JAVA_LANG_BYTE ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_BYTE, PsiTypes.byteType(), "byteValue");
      case CommonClassNames.JAVA_LANG_CHARACTER ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_CHARACTER, PsiTypes.charType(), "charValue");
      case CommonClassNames.JAVA_LANG_LONG ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_LONG, PsiTypes.longType(), "longValue");
      case CommonClassNames.JAVA_LANG_FLOAT ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_FLOAT, PsiTypes.floatType(), "floatValue");
      case CommonClassNames.JAVA_LANG_DOUBLE ->
        MethodCallUtils.isCallToMethod(methodCallExpression, CommonClassNames.JAVA_LANG_DOUBLE, PsiTypes.doubleType(), "doubleValue");
      default -> false;
    };
    if (shouldReplace) {
      expression.replace(qualifierExpression);
      return true;
    }
    return false;
  }

  private static class AutoBoxingFix extends PsiUpdateModCommandQuickFix {
    @Override
    @NotNull
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("auto.boxing.make.boxing.explicit.quickfix");
    }

    @Override
    protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
      final PsiExpression expression = (PsiExpression)element;
      replaceWithBoxing(expression);
    }
  }

  private class AutoBoxingVisitor extends BaseInspectionVisitor {

    @Override
    public void visitSwitchExpression(@NotNull PsiSwitchExpression expression) {
      super.visitSwitchExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitArrayAccessExpression(@NotNull PsiArrayAccessExpression expression) {
      super.visitArrayAccessExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression) {
      super.visitAssignmentExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitConditionalExpression(@NotNull PsiConditionalExpression expression) {
      super.visitConditionalExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitInstanceOfExpression(@NotNull PsiInstanceOfExpression expression) {
      super.visitInstanceOfExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
      super.visitMethodCallExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitParenthesizedExpression(@NotNull PsiParenthesizedExpression expression) {
      super.visitParenthesizedExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitUnaryExpression(@NotNull PsiUnaryExpression expression) {
      super.visitUnaryExpression(expression);
      checkExpression(expression);
    }

    @Override
    public void visitReferenceExpression(@NotNull PsiReferenceExpression expression) {
      super.visitReferenceExpression(expression);
      if (expression instanceof PsiMethodReferenceExpression methodReferenceExpression) {
        if (methodReferenceExpression.isConstructor()) {
          return;
        }
        final PsiElement referenceNameElement = methodReferenceExpression.getReferenceNameElement();
        if (referenceNameElement == null) {
          return;
        }
        final PsiElement target = methodReferenceExpression.resolve();
        if (!(target instanceof PsiMethod method)) {
          return;
        }
        final PsiType returnType = method.getReturnType();
        if (returnType == null || returnType.equals(PsiTypes.voidType()) || !TypeConversionUtil.isPrimitiveAndNotNull(returnType)) {
          return;
        }
        final PsiPrimitiveType primitiveType = (PsiPrimitiveType)returnType;
        final PsiClassType boxedType = primitiveType.getBoxedType(expression);
        if (boxedType == null) {
          return;
        }
        final PsiType functionalInterfaceReturnType = LambdaUtil.getFunctionalInterfaceReturnType(methodReferenceExpression);
        if (functionalInterfaceReturnType == null || TypeConversionUtil.isPrimitiveAndNotNull(functionalInterfaceReturnType) ||
            !functionalInterfaceReturnType.isAssignableFrom(boxedType)) {
          return;
        }
        registerError(referenceNameElement);
      }
      else {
        checkExpression(expression);
      }
    }

    @Override
    public void visitTypeCastExpression(@NotNull PsiTypeCastExpression expression) {
      super.visitTypeCastExpression(expression);
      checkExpression(expression);
    }

    private void checkExpression(@NotNull PsiExpression expression) {
      if (!ExpressionUtils.isAutoBoxed(expression)) {
        return;
      }
      if (ignoreAddedToCollection && isAddedToCollection(expression)) {
        return;
      }
      registerError(expression, expression);
    }

    private static boolean isAddedToCollection(PsiExpression expression) {
      final PsiElement parent = expression.getParent();
      if (!(parent instanceof PsiExpressionList expressionList)) {
        return false;
      }
      final PsiElement grandParent = expressionList.getParent();
      if (!(grandParent instanceof PsiMethodCallExpression methodCallExpression)) {
        return false;
      }
      final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
      @NonNls final String methodName = methodExpression.getReferenceName();
      if (!"put".equals(methodName) && !"set".equals(methodName) && !"add".equals(methodName)) {
        return false;
      }
      final PsiExpression qualifier = methodExpression.getQualifierExpression();
      return TypeUtils.expressionHasTypeOrSubtype(qualifier, CommonClassNames.JAVA_UTIL_COLLECTION, CommonClassNames.JAVA_UTIL_MAP) != null;
    }
  }
}