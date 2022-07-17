// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.util.CommonJavaRefactoringUtil;

import java.util.Map;

public class ParameterData {
  private final String myParameterName;
  private final PsiType myType;
  private String myFieldName;
  private String mySetterName;
  private String myDefaultValue;
  private boolean myInsertSetter = true;
  private static final Logger LOG = Logger.getInstance(ParameterData.class);

  public ParameterData(String parameterName, PsiType type) {
    myParameterName = parameterName;
    myType = type;
  }

  public static void createFromConstructor(final PsiMethod constructor, String setterPrefix, final Map<String, ParameterData> result) {

    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      initParameterData(parameter, setterPrefix, result);
    }

    final PsiMethod chainedConstructor = CommonJavaRefactoringUtil.getChainedConstructor(constructor);
    if (chainedConstructor != null) {
      final PsiCodeBlock constructorBody = constructor.getBody();
      LOG.assertTrue(constructorBody != null);
      final PsiStatement thisStatement = constructorBody.getStatements()[0];
      final PsiExpression thisExpression = ((PsiExpressionStatement)thisStatement).getExpression();
      final PsiExpression[] args = ((PsiMethodCallExpression)thisExpression).getArgumentList().getExpressions();
      int i = 0;
      for (final PsiParameter parameter : chainedConstructor.getParameterList().getParameters()) {
        if (!parameter.isVarArgs()) {
          final PsiExpression arg = args[i];
          final ParameterData parameterData = initParameterData(parameter, setterPrefix, result);
          if (!(arg instanceof PsiReferenceExpression && ((PsiReferenceExpression)arg).resolve() instanceof PsiParameter)) {
            parameterData.setDefaultValue(arg.getText());
          }
          i++;
        }
      }
    }
  }

  private static ParameterData initParameterData(PsiParameter parameter, String setterPrefix, Map<String, ParameterData> result) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(parameter.getProject());
    final String paramName = parameter.getName();
    final String pureParamName = styleManager.variableNameToPropertyName(paramName, VariableKind.PARAMETER);

    String uniqueParamName = pureParamName;
    ParameterData parameterData = result.get(uniqueParamName);
    int i = 0;
    while (parameterData != null) {
      if (!Comparing.equal(parameter.getType(), parameterData.getType())) {
        uniqueParamName = pureParamName + i++;
        parameterData = result.get(uniqueParamName);
      } else {
        break;
      }
    }
    if (parameterData == null) {
      parameterData = new ParameterData(paramName, parameter.getType());

      parameterData.setFieldName(styleManager.suggestVariableName(VariableKind.FIELD, uniqueParamName, null, parameter.getType()).names[0]);
      parameterData.setSetterName(PropertyUtilBase.suggestSetterName(uniqueParamName, setterPrefix));

      result.put(uniqueParamName, parameterData);
    }

    return parameterData;
  }

  public PsiType getType() {
    return myType;
  }

  public String getFieldName() {
    return myFieldName;
  }

  public String getSetterName() {
    return mySetterName;
  }

  public String getParamName() {
    return myParameterName;
  }

  public String getDefaultValue() {
    return myDefaultValue;
  }

  public boolean isInsertSetter() {
    return myInsertSetter;
  }

  public void setFieldName(String fieldName) {
    myFieldName = fieldName;
  }

  public void setSetterName(String setterName) {
    mySetterName = setterName;
  }

  public void setDefaultValue(String defaultValue) {
    myDefaultValue = defaultValue;
  }

  public void setInsertSetter(boolean insertSetter) {
    myInsertSetter = insertSetter;
  }
}