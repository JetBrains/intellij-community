// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class AtomicConstructorConversionDescriptor extends ArrayInitializerAwareConversionDescriptor {
  private final @NotNull AtomicConversionType myType;

  AtomicConstructorConversionDescriptor(@NonNls String stringToReplace,
                                        @NonNls String replaceByString,
                                        PsiExpression expression,
                                        @NotNull AtomicConversionType type) {
    super(stringToReplace, replaceByString, expression);
    myType = type;
  }

  @Override
  public PsiExpression replace(PsiExpression expression, @NotNull TypeEvaluator evaluator) {
    PsiNewExpression constructorCall = (PsiNewExpression)super.replace(expression, evaluator);
    PsiExpression argument = Objects.requireNonNull(constructorCall.getArgumentList()).getExpressions()[0];
    if (myType.checkDefaultValue(argument)) {
      argument.delete();
    }
    return constructorCall;
  }
}
