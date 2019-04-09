// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethodWithResultObject;

import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Predicate;

import static com.intellij.util.ObjectUtils.notNull;

/**
 * @author Pavel.Dolgov
 */
abstract class ResultItem {
  static final String EXPRESSION_RESULT = "expressionResult";
  static final String RETURN_RESULT = "returnResult";
  static final String EXIT_KEY = "exitKey";

  protected final String myFieldName; // todo resolve name conflicts
  protected final PsiType myType;

  ResultItem(String fieldName, PsiType type) {
    myFieldName = fieldName;
    myType = type;
  }

  void createField(PsiClass resultClass, PsiElementFactory factory) {
    PsiField field = (PsiField)resultClass.add(factory.createField(myFieldName, myType));
    notNull(field.getModifierList()).setModifierProperty(PsiModifier.PRIVATE, true);
  }

  void createConstructorParameter(PsiParameterList parameterList, PsiElementFactory factory) {
    parameterList.add(factory.createParameter(myFieldName, myType));
  }

  void createAssignmentInConstructor(PsiCodeBlock body, PsiElementFactory factory) {
    body.add(factory.createStatementFromText(PsiKeyword.THIS + '.' + myFieldName + '=' + myFieldName + ';', body.getRBrace()));
  }

  @NotNull
  PsiExpression createMissingValue(PsiCodeBlock body, PsiElementFactory factory) {
    String text = myType instanceof PsiPrimitiveType ? PsiTypesUtil.getDefaultValueOfType(myType) : PsiKeyword.NULL;
    return factory.createExpressionFromText("(" + text + " /* missing value */)", body.getRBrace());
  }

  void contributeToTypeParameters(List<PsiElement> elements) {}

  abstract PsiExpression createResultObjectArgument(int exitKey,
                                                    PsiExpression expression,
                                                    PsiCodeBlock body,
                                                    Predicate<PsiVariable> isUndefined,
                                                    PsiElementFactory factory);

  static class Variable extends ResultItem {
    final PsiVariable myVariable;
    final String myVariableName;

    Variable(@NotNull PsiVariable variable, @NotNull String variableName) {
      super(variableName, ExtractUtil.getVariableType(variable));
      myVariable = variable;
      myVariableName = variableName;
    }

    @Override
    PsiExpression createResultObjectArgument(int exitKey,
                                             PsiExpression expression,
                                             PsiCodeBlock body,
                                             Predicate<PsiVariable> isUndefined,
                                             PsiElementFactory factory) {
      if (isUndefined.test(myVariable)) {
        return createMissingValue(body, factory);
      }
      return factory.createExpressionFromText(myFieldName, body.getRBrace());
    }

    @Override
    void contributeToTypeParameters(List<PsiElement> elements) {
      elements.add(myVariable);
    }
  }

  static class Expression extends ResultItem {
    private final PsiExpression myExpression;

    Expression(@NotNull PsiExpression expression, PsiType type) {
      super(EXPRESSION_RESULT, type);
      myExpression = expression;
    }

    @Override
    PsiExpression createResultObjectArgument(int exitKey,
                                             PsiExpression expression,
                                             PsiCodeBlock body,
                                             Predicate<PsiVariable> isUndefined,
                                             PsiElementFactory factory) {
      return myExpression;
    }
  }

  static class Return extends ResultItem {
    private final PsiTypeElement myReturnTypeElement;

    Return(@NotNull PsiType returnType, @Nullable PsiTypeElement returnTypeElement) {
      super(RETURN_RESULT, returnType);
      myReturnTypeElement = returnTypeElement;
    }

    @Override
    PsiExpression createResultObjectArgument(int exitKey,
                                             PsiExpression expression,
                                             PsiCodeBlock body,
                                             Predicate<PsiVariable> isUndefined,
                                             PsiElementFactory factory) {
      if (expression == null) {
        return createMissingValue(body, factory);
      }
      return expression;
    }

    @Override
    void contributeToTypeParameters(List<PsiElement> elements) {
      if (myReturnTypeElement != null) {
        elements.add(myReturnTypeElement);
      }
    }
  }

  static class ExitKey extends ResultItem {
    ExitKey() {
      super(EXIT_KEY, PsiType.INT);
    }

    @Override
    PsiExpression createResultObjectArgument(int exitKey,
                                             PsiExpression expression,
                                             PsiCodeBlock body,
                                             Predicate<PsiVariable> isUndefined,
                                             PsiElementFactory factory) {
      return factory.createExpressionFromText("(" + exitKey + " /* exit key */)", body.getRBrace());
    }
  }
}
