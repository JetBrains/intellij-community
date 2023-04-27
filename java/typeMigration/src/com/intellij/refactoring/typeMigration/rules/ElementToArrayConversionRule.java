// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptorBase;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import com.intellij.refactoring.typeMigration.TypeMigrationLabeler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ElementToArrayConversionRule extends TypeConversionRule {
  @Override
  public TypeConversionDescriptorBase findConversion(PsiType from, PsiType to, PsiMember member, PsiExpression context,
                                                     TypeMigrationLabeler labeler) {
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
        PsiType finalComponentType = componentType;
        return new TypeConversionDescriptorBase() {
          @Override
          public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
            String text = "{".repeat(dimensions) + context.getText() + "}".repeat(dimensions);
            if (!(expression.getParent() instanceof PsiVariable variable && variable.getType().equals(finalComponentType))) {
              text = "new " + finalComponentType.getCanonicalText() + "[]".repeat(dimensions) + text;
            }
            PsiExpression newExpression =
              JavaPsiFacade.getElementFactory(expression.getProject()).createExpressionFromText(text, expression);
            return (PsiExpression)expression.replace(newExpression);
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