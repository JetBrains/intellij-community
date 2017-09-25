/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.refactoring.typeMigration.rules;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiNewExpression;
import com.intellij.refactoring.typeMigration.TypeEvaluator;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

class AtomicConstructorConversionDescriptor extends ArrayInitializerAwareConversionDescriptor {
  @NotNull
  private final AtomicConversionType myType;

  public AtomicConstructorConversionDescriptor(String stringToReplace,
                                               String replaceByString,
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
