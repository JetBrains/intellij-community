// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ElementToArrayConversionRule extends TypeConversionRule {
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from, PsiType to, PsiMember member, PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
    if (member != null && from instanceof PsiEllipsisType && to instanceof PsiArrayType && !(to instanceof PsiEllipsisType)) {
      from = ((PsiEllipsisType)from).getComponentType();
    }
    final int dimensions = to.getArrayDimensions() - from.getArrayDimensions();
    if (dimensions < 0) {
      final PsiExpression expression = unwrap(context, -dimensions);
      if (expression == null) return null;
      final PsiType type = expression.getType();
      if (type != null && to.isAssignableFrom(type)) {
        return new TypeConversionDescriptorBase() {
          @Override
          public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
            PsiExpression unwrapped = unwrap(expression, -dimensions);
            if (unwrapped == null) {
              return expression;
            }
            return (PsiExpression)expression.replace(unwrapped);
          }
        };
      }
    }
    if (dimensions > 0 && to instanceof PsiArrayType arrayType && shouldConvert(context)) {
      PsiType componentType = arrayType;
      for (int i = 0; i < dimensions; i++) {
        assert componentType instanceof PsiArrayType;
        componentType = ((PsiArrayType)componentType).getComponentType();
      }
      componentType = TypeConversionUtil.erasure(componentType);
      if (componentType.isAssignableFrom(from)) {
        final boolean vararg = member instanceof PsiMethod method && method.isVarArgs();
        final PsiType finalComponentType = componentType;
        return new TypeConversionDescriptorBase() {
          @Override
          public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
            String content = vararg ? getVarargText(expression) : expression.getText();
            String text = "{".repeat(dimensions) + content + "}".repeat(dimensions);
            PsiElement parent = expression.getParent();
            if (!(parent instanceof PsiVariable variable && variable.getType().equals(finalComponentType))) {
              text = "new " + finalComponentType.getCanonicalText() + "[]".repeat(dimensions) + text;
            }
            PsiExpression newExpression =
              JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(text, expression);
            if (vararg) {
              PsiElement last = expression;
              PsiElement anchor = last.getNextSibling();
              while (anchor != null && !PsiUtil.isJavaToken(anchor, JavaTokenType.RPARENTH)) {
                last = anchor;
                anchor = anchor.getNextSibling();
              }
              parent.deleteChildRange(expression, last);
              return (PsiExpression)parent.add(newExpression);
            }
            else {
              return (PsiExpression)expression.replace(newExpression);
            }
          }

          private static String getVarargText(PsiElement element) {
            final StringBuilder result = new StringBuilder();
            while (element != null && !PsiUtil.isJavaToken(element, JavaTokenType.RPARENTH)) {
              result.append(element.getText());
              element = element.getNextSibling();
            }
            return result.toString();
          }
        };
      }
    }
    return null;
  }

  private static boolean shouldConvert(PsiExpression expression) {
    return !(expression instanceof PsiReferenceExpression) &&
           !(expression.getParent() instanceof PsiExpressionStatement);
  }

  @Nullable
  private static PsiExpression unwrap(PsiExpression expression, int dimensions) {
    for (int i = 0; i < dimensions; i++) {
      final PsiArrayInitializerExpression arrayInitializer;
      if ((expression instanceof PsiNewExpression newExpression)) {
        arrayInitializer = newExpression.getArrayInitializer();
      }
      else if (expression instanceof PsiArrayInitializerExpression) {
        arrayInitializer = (PsiArrayInitializerExpression)expression;
      }
      else {
        return null;
      }
      if (arrayInitializer == null) return null;
      PsiExpression[] initializers = arrayInitializer.getInitializers();
      if (initializers.length != 1) return null;
      expression = initializers[0];
    }
    return expression;
  }
}