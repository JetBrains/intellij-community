// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.util;

import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public final class EnumConstantsUtil {
  private EnumConstantsUtil() {
  }

  public static boolean isSuitableForEnumConstant(PsiType constantType, PsiClass enumClass) {
    if (enumClass != null && enumClass.isEnum()) {
      for (PsiMethod constructor : enumClass.getConstructors()) {
        final PsiParameter[] parameters = constructor.getParameterList().getParameters();
        if (parameters.length == 1 && TypeConversionUtil.isAssignable(parameters[0].getType(), constantType)) return true;
      }
    }
    return false;
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, String constantName, PsiExpression initializerExpr) throws
                                                                                                                              IncorrectOperationException {
    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(enumClass.getProject());
    final String enumConstantText = initializerExpr != null ? constantName + "(" + initializerExpr.getText() + ")" : constantName;
    return elementFactory.createEnumConstantFromText(enumConstantText, enumClass);
  }

  public static PsiEnumConstant createEnumConstant(PsiClass enumClass, PsiLocalVariable local, final String fieldName) throws IncorrectOperationException {
    return createEnumConstant(enumClass, fieldName, local.getInitializer());
  }

  @Nullable
  public static PsiStatement isEnumSwitch(final PsiSwitchStatement switchStatement,
                                          final PsiType enumValueType,
                                          final Set<Object> enumValues) {
    final PsiExpression expression = switchStatement.getExpression();
    if (expression != null) {
      final PsiType expressionType = expression.getType();
      if (expressionType != null && !TypeConversionUtil.isAssignable(expressionType, enumValueType)) {
        return switchStatement;
      }
      final PsiConstantEvaluationHelper evaluationHelper =
        JavaPsiFacade.getInstance(expression.getProject()).getConstantEvaluationHelper();
      final PsiCodeBlock body = switchStatement.getBody();
      if (body != null) {
        for (PsiStatement statement : body.getStatements()) {
          if (statement instanceof PsiSwitchLabelStatement labelStatement) {
            final Object caseValue = evaluationHelper.computeConstantExpression(labelStatement.getCaseValue());
            if (caseValue != null && !enumValues.contains(caseValue)) return statement;
          }
        }
        return null;
      }
    }
    return switchStatement;
  }
}