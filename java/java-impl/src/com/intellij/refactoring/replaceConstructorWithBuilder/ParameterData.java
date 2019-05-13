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

package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.psi.util.PropertyUtilBase;
import com.intellij.refactoring.util.RefactoringUtil;

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

    final PsiMethod chainedConstructor = RefactoringUtil.getChainedConstructor(constructor);
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