/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.JavaConstantExpressionEvaluator;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Danila Ponomarenko
 */
public class ConvertColorRepresentationIntentionAction extends BaseColorIntentionAction {

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    if (!super.isAvailable(project, editor, element)) {
      return false;
    }

    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return false;
    }

    final PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) {
      return false;
    }

    final PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) {
      return false;
    }

    final PsiExpressionList newArguments = createNewArguments(JavaPsiFacade.getElementFactory(project), constructor.getParameterList().getParameters(), arguments.getExpressions());

    if (newArguments == null) {
      return false;
    }

    setText(CodeInsightBundle.message("intention.convert.color.representation.text", newArguments.getText()));

    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final PsiNewExpression expression = PsiTreeUtil.getParentOfType(element, PsiNewExpression.class, false);
    if (expression == null) {
      return;
    }

    final PsiExpressionList arguments = expression.getArgumentList();
    if (arguments == null) {
      return;
    }

    final PsiMethod constructor = expression.resolveConstructor();
    if (constructor == null) {
      return;
    }

    final PsiExpressionList newArguments = createNewArguments(
      JavaPsiFacade.getElementFactory(project),
      constructor.getParameterList().getParameters(),
      arguments.getExpressions()
    );

    if (newArguments == null) {
      return;
    }

    arguments.replace(newArguments);
  }

  @Nullable
  private static PsiExpressionList createNewArguments(@NotNull PsiElementFactory factory,
                                                      @NotNull PsiParameter[] parameters,
                                                      @NotNull PsiExpression[] arguments) {
    final String[] newValues = createArguments(parameters, arguments);
    if (newValues == null) {
      return null;
    }

    final PsiExpressionList result = ((PsiNewExpression)factory.createExpressionFromText("new Object()", parameters[0])).getArgumentList();
    if (result == null) {
      return null;
    }
    for (String value : newValues) {
      result.add(factory.createExpressionFromText(value, parameters[0]));
    }
    return result;
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiParameter[] parameters,
                                          @NotNull PsiExpression[] arguments) {
    if (parameters.length != arguments.length) {
      return null;
    }

    switch (parameters.length) {
      default:
        return null;
      case 1:
        return createArguments(arguments[0]);
      case 2:
        return createArguments(arguments[0], arguments[1]);
      case 3:
        return createArguments(arguments[0], arguments[1], arguments[2]);
      case 4:
        return createArguments(arguments[0], arguments[1], arguments[2], arguments[3]);
    }
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiExpression rgbExpression) {
    return createArguments(rgbExpression, 3);
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiExpression rgbExpression,
                                          @NotNull PsiExpression hasAlphaExpression) {
    final Boolean hasAlpha = computeBoolean(hasAlphaExpression);
    if (hasAlpha == null) {
      return null;
    }
    return hasAlpha ? createArguments(rgbExpression, 4) : createArguments(rgbExpression);
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiExpression rExpression,
                                          @NotNull PsiExpression gExpression,
                                          @NotNull PsiExpression bExpression) {
    final Integer value = createInt(computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression));
    return value != null ? new String[]{"0x" + Integer.toHexString(value)} : null;
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiExpression rExpression,
                                          @NotNull PsiExpression gExpression,
                                          @NotNull PsiExpression bExpression,
                                          @NotNull PsiExpression aExpression) {
    final Integer value = createInt(computeInteger(rExpression), computeInteger(gExpression), computeInteger(bExpression), computeInteger(aExpression));
    if (value == null) {
      return null;
    }

    return new String[]{
      "0x" + Integer.toHexString(value),
      "true",
    };
  }

  @Nullable
  private static String[] createArguments(@NotNull PsiExpression rgbExpression,
                                          int parts) {
    final Integer rgb = computeInteger(rgbExpression);
    if (rgb == null) {
      return null;
    }

    final String[] result = new String[parts];
    for (int i = 0; i < result.length; i++) {
      result[result.length - i - 1] = String.valueOf(rgb >> (i * Byte.SIZE) & 0xFF);
    }
    return result;
  }

  @Nullable
  private static Integer createInt(Integer... ints) {
    int result = 0;
    for (Integer i : ints) {
      if (i == null) {
        return null;
      }
      result = result << Byte.SIZE | (i & 0xFF);
    }
    return result;
  }

  @Nullable
  public static Integer computeInteger(@NotNull PsiExpression expr) {
    final Object result = compute(expr);
    return result instanceof Integer ? (Integer)result : null;
  }

  @Nullable
  public static Boolean computeBoolean(@NotNull PsiExpression expr) {
    final Object result = compute(expr);
    return result instanceof Boolean ? (Boolean)result : null;
  }

  @Nullable
  private static Object compute(@NotNull PsiExpression expr) {
    return JavaConstantExpressionEvaluator.computeConstantExpression(expr, true);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.convert.color.representation.family");
  }
}
