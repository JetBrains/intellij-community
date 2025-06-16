/*
 * Copyright 2003-2014 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.internationalization;

import com.intellij.codeInsight.AnnotationUtil;
import com.intellij.codeInsight.intention.AddAnnotationModCommandAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.fixes.SuppressForTestsScopeFix;
import com.siyeh.ig.psiutils.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public final class StringConcatenationInspection extends BaseInspection {

  @SuppressWarnings("PublicField")
  public boolean ignoreAsserts = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreSystemOuts = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreSystemErrs = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreThrowableArguments = false;

  @SuppressWarnings("PublicField")
  public boolean ignoreConstantInitializers = false;

  @SuppressWarnings({"PublicField", "UnusedDeclaration"})
  public boolean ignoreInTestCode = false; // keep for compatibility

  @SuppressWarnings("PublicField")
  public boolean ignoreInToString = false;

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    return InspectionGadgetsBundle.message("string.concatenation.problem.descriptor");
  }

  @Override
  protected LocalQuickFix @NotNull [] buildFixes(Object... infos) {
    final PsiPolyadicExpression polyadicExpression = (PsiPolyadicExpression)infos[0];
    final Collection<LocalQuickFix> result = new ArrayList<>();
    final PsiElement parent = ParenthesesUtils.getParentSkipParentheses(polyadicExpression);
    if (parent instanceof PsiVariable variable) {
      ContainerUtil.addIfNotNull(result, createAddAnnotationFix(variable));
    }
    else if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiExpression lhs = assignmentExpression.getLExpression();
      if (lhs instanceof PsiReferenceExpression referenceExpression) {
        final PsiElement target = referenceExpression.resolve();
        if (target instanceof PsiModifierListOwner modifierListOwner) {
          ContainerUtil.addIfNotNull(result, createAddAnnotationFix(modifierListOwner));
        }
      }
    }
    else if (parent instanceof PsiExpressionList) {
      final PsiElement grandParent = parent.getParent();
      if (grandParent instanceof PsiMethodCallExpression methodCallExpression) {
        final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
        final PsiExpression qualifierExpression = methodExpression.getQualifierExpression();
        if (qualifierExpression instanceof PsiReferenceExpression referenceExpression) {
          final PsiElement target = referenceExpression.resolve();
          if (target instanceof PsiModifierListOwner modifierListOwner) {
            ContainerUtil.addIfNotNull(result, createAddAnnotationFix(modifierListOwner));
          }
        }
      }
    }
    final PsiExpression[] operands = polyadicExpression.getOperands();
    for (PsiExpression operand : operands) {
      final PsiModifierListOwner element1 = getAnnotatableElement(operand);
      if (element1 != null) {
        ContainerUtil.addIfNotNull(result, createAddAnnotationFix(element1));
      }
    }
    final PsiElement expressionParent = PsiTreeUtil.getParentOfType(polyadicExpression, PsiReturnStatement.class, PsiExpressionList.class);
    if (!(expressionParent instanceof PsiExpressionList) && expressionParent != null) {
      final PsiMethod method = PsiTreeUtil.getParentOfType(expressionParent, PsiMethod.class);
      if (method != null) {
        ContainerUtil.addIfNotNull(result, createAddAnnotationFix(method));
      }
    }

    final SuppressForTestsScopeFix suppressFix = SuppressForTestsScopeFix.build(this, polyadicExpression);
    if (suppressFix == null) {
      return result.toArray(LocalQuickFix.EMPTY_ARRAY);
    }
    result.add(suppressFix);
   
    return result.toArray(LocalQuickFix.EMPTY_ARRAY);
  }

  private static LocalQuickFix createAddAnnotationFix(PsiModifierListOwner listOwner) {
    if (listOwner.getManager().isInProject(listOwner) && 
        JavaPsiFacade.getInstance(listOwner.getProject()).findClass(AnnotationUtil.NON_NLS, 
                                                                    listOwner.getResolveScope()) == null) {
      return null;
    }
    return LocalQuickFix.from(new AddAnnotationModCommandAction(AnnotationUtil.NON_NLS, listOwner));
  }

  public static @Nullable PsiModifierListOwner getAnnotatableElement(PsiExpression expression) {
    if (!(expression instanceof PsiReferenceExpression referenceExpression)) {
      return null;
    }
    if (!TypeUtils.isJavaLangString(referenceExpression.getType())) return null;
    final PsiElement element = referenceExpression.resolve();
    if (!(element instanceof PsiModifierListOwner) || !element.isPhysical()) {
      return null;
    }
    return (PsiModifierListOwner)element;
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("ignoreAsserts", InspectionGadgetsBundle.message("inspection.option.ignore.assert")),
      checkbox("ignoreSystemOuts", InspectionGadgetsBundle.message("inspection.option.ignore.system.out")),
      checkbox("ignoreSystemErrs", InspectionGadgetsBundle.message("inspection.option.ignore.system.err")),
      checkbox("ignoreThrowableArguments", InspectionGadgetsBundle.message("inspection.option.ignore.exceptions")),
      checkbox("ignoreConstantInitializers", InspectionGadgetsBundle.message("inspection.option.ignore.constant.initializers")),
      checkbox("ignoreInToString", InspectionGadgetsBundle.message("inspection.option.ignore.in.tostring")));
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new StringConcatenationVisitor();
  }

  private class StringConcatenationVisitor extends BaseInspectionVisitor {

    @Override
    public void visitPolyadicExpression(@NotNull PsiPolyadicExpression expression) {
      super.visitPolyadicExpression(expression);
      final IElementType tokenType = expression.getOperationTokenType();
      if (!JavaTokenType.PLUS.equals(tokenType)) {
        return;
      }
      final PsiType type = expression.getType();
      if (!TypeUtils.isJavaLangString(type)) {
        return;
      }
      final PsiExpression[] operands = expression.getOperands();
      for (PsiExpression operand : operands) {
        if (NonNlsUtils.isNonNlsAnnotated(operand)) {
          return;
        }
      }
      if (AnnotationUtil.isInsideAnnotation(expression)) {
        return;
      }
      if (ignoreAsserts) {
        final PsiAssertStatement assertStatement =
          PsiTreeUtil.getParentOfType(expression, PsiAssertStatement.class, true, PsiCodeBlock.class, PsiClass.class);
        if (assertStatement != null) {
          return;
        }
      }
      if (ignoreSystemErrs || ignoreSystemOuts) {
        final PsiMethodCallExpression methodCallExpression =
          PsiTreeUtil.getParentOfType(expression, PsiMethodCallExpression.class, true, PsiCodeBlock.class, PsiClass.class);
        if (methodCallExpression != null) {
          final PsiReferenceExpression methodExpression = methodCallExpression.getMethodExpression();
          final @NonNls String canonicalText = methodExpression.getCanonicalText();
          if (ignoreSystemOuts && "System.out.println".equals(canonicalText) || "System.out.print".equals(canonicalText)) {
            return;
          }
          if (ignoreSystemErrs && "System.err.println".equals(canonicalText) || "System.err.print".equals(canonicalText)) {
            return;
          }
        }
      }
      if (ignoreThrowableArguments) {
        if (ExceptionUtils.isExceptionArgument(expression)) return;
      }
      if (ignoreConstantInitializers) {
        PsiElement parent = expression.getParent();
        while (parent instanceof PsiBinaryExpression) {
          parent = parent.getParent();
        }
        if (parent instanceof PsiField field) {
          if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.FINAL)) {
            return;
          }
          final PsiClass containingClass = field.getContainingClass();
          if (containingClass != null && containingClass.isInterface()) {
            return;
          }
        }
      }
      if (ignoreInToString) {
        final PsiMethod method = PsiTreeUtil.getParentOfType(expression, PsiMethod.class, true, PsiClass.class, PsiLambdaExpression.class);
        if (MethodUtils.isToString(method)) {
          return;
        }
      }
      if (NonNlsUtils.isNonNlsAnnotatedUse(expression)) {
        return;
      }
      for (int i = 1; i < operands.length; i++) {
        final PsiExpression operand = operands[i];
        if (!ExpressionUtils.isStringConcatenationOperand(operand)) {
          continue;
        }
        final PsiJavaToken token = expression.getTokenBeforeOperand(operand);
        if (token == null) {
          continue;
        }
        registerError(token, expression);
      }
    }
  }
}
