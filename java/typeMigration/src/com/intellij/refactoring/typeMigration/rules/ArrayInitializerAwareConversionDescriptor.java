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

import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiArrayInitializerExpression;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.refactoring.typeMigration.TypeConversionDescriptor;
import org.jetbrains.annotations.NotNull;

class ArrayInitializerAwareConversionDescriptor extends TypeConversionDescriptor {
  public ArrayInitializerAwareConversionDescriptor(String stringToReplace,
                                                   String replaceByString,
                                                   PsiExpression expression) {
    super(stringToReplace, replaceByString, expression);
  }

  @NotNull
  @Override
  protected PsiExpression adjustExpressionBeforeReplacement(@NotNull PsiExpression expression) {
    if (expression instanceof PsiArrayInitializerExpression) {
      PsiElementFactory elementFactory = JavaPsiFacade.getInstance(expression.getProject()).getElementFactory();
      return (PsiExpression)expression.replace(elementFactory.createExpressionFromText("new " +
                                                                                       TypeConversionUtil
                                                                                         .erasure(expression.getType()).getCanonicalText() +
                                                                                       expression.getText(),
                                                                                       expression));
    }
    return expression;
  }
}
