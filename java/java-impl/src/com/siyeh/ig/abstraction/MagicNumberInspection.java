/*
 * Copyright 2003-2013 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.IntroduceConstantFix;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.ExpressionUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class MagicNumberInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreInHashCode = true;
  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility
  @SuppressWarnings("PublicField")
  public boolean ignoreInAnnotations = true;
  @SuppressWarnings("PublicField")
  public boolean ignoreInitialCapacity = false;

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiElement context = (PsiElement)infos[0];
    final LocalQuickFix fix = SuppressForTestsScopeFix.build(this, context);
    if (fix == null) {
      return new InspectionGadgetsFix[] {new IntroduceConstantFix()};
    }
    return new LocalQuickFix[] {new IntroduceConstantFix(), fix};
  }

  @Override
  protected boolean buildQuickFixesOnlyForOnTheFlyErrors() {
    return true;
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("magic.number.problem.descriptor");
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreInHashCode", InspectionGadgetsBundle.message("inspection.option.ignore.in.hashcode")),
      checkbox("ignoreInAnnotations", InspectionGadgetsBundle.message("inspection.option.ignore.in.annotations")),
      checkbox("ignoreInitialCapacity", InspectionGadgetsBundle.message("inspection.option.ignore.as.initial.capacity")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MagicNumberVisitor();
  }

  private class MagicNumberVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLiteralExpression(@NotNull PsiLiteralExpression expression) {
      super.visitLiteralExpression(expression);
      final PsiType type = expression.getType();
      if (!ClassUtils.isPrimitiveNumericType(type) || PsiTypes.charType().equals(type)) {
        return;
      }
      if (isSpecialCaseLiteral(expression) || isFinalVariableInitialization(expression)) {
        return;
      }
      if (ignoreInHashCode) {
        final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true,
                                                                       PsiClass.class, PsiLambdaExpression.class);
        if (MethodUtils.isHashCode(containingMethod)) {
          return;
        }
      }
      if (ignoreInAnnotations) {
        final boolean insideAnnotation = AnnotationUtil.isInsideAnnotation(expression);
        if (insideAnnotation) {
          return;
        }
      }
      if (ignoreInitialCapacity && isInitialCapacity(expression)) {
        return;
      }
      final PsiField field = PsiTreeUtil.getParentOfType(expression, PsiField.class, true, PsiCallExpression.class);
      if (field != null && PsiUtil.isCompileTimeConstant(field)) {
        return;
      }
      final PsiElement parent = expression.getParent();
      if (parent instanceof PsiPrefixExpression) {
        registerError(parent, parent);
      }
      else {
        registerError(expression, expression);
      }
    }

    private static boolean isInitialCapacity(PsiLiteralExpression expression) {
      final PsiElement element =
        PsiTreeUtil.skipParentsOfType(expression, PsiTypeCastExpression.class, PsiParenthesizedExpression.class);
      if (!(element instanceof PsiExpressionList)) {
        return false;
      }
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiNewExpression newExpression)) {
        return false;
      }
      return TypeUtils.expressionHasTypeOrSubtype(newExpression,
                                                  CommonClassNames.JAVA_LANG_ABSTRACT_STRING_BUILDER,
                                                  CommonClassNames.JAVA_UTIL_MAP,
                                                  CommonClassNames.JAVA_UTIL_COLLECTION,
                                                  "java.io.ByteArrayOutputStream",
                                                  "java.awt.Dimension") != null;
    }

    private static boolean isSpecialCaseLiteral(PsiLiteralExpression expression) {
      final Object object = ExpressionUtils.computeConstantExpression(expression);
      if (object instanceof Integer) {
        final int i = ((Integer)object).intValue();
        return i >= 0 && i <= 10 || i == 100 || i == 1000;
      }
      else if (object instanceof Long) {
        final long l = ((Long)object).longValue();
        return l >= 0L && l <= 2L;
      }
      else if (object instanceof Double) {
        final double d = ((Double)object).doubleValue();
        return d == 1.0 || d == 0.0;
      }
      else if (object instanceof Float) {
        final float f = ((Float)object).floatValue();
        return f == 1.0f || f == 0.0f;
      }
      return false;
    }

    public boolean isFinalVariableInitialization(PsiExpression expression) {
      final PsiElement parent =
        PsiTreeUtil.getParentOfType(expression, PsiVariable.class, PsiAssignmentExpression.class);
      final PsiVariable variable;
      if (!(parent instanceof PsiVariable)) {
        if (!(parent instanceof PsiAssignmentExpression assignmentExpression)) {
          return false;
        }
        final PsiExpression lhs = assignmentExpression.getLExpression();
        if (!(lhs instanceof PsiReferenceExpression referenceExpression)) {
          return false;
        }
        final PsiElement target = referenceExpression.resolve();
        if (!(target instanceof PsiVariable)) {
          return false;
        }
        variable = (PsiVariable)target;
      }
      else {
        variable = (PsiVariable)parent;
      }
      return variable.hasModifierProperty(PsiModifier.FINAL);
    }
  }
}
