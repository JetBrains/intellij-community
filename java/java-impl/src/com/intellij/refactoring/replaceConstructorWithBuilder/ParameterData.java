/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 04-Feb-2009
 */
package com.intellij.refactoring.replaceConstructorWithBuilder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.refactoring.util.RefactoringUtil;

import java.util.Map;

public class ParameterData {
  private final String myName;
  private final String myParameterName;
  private final PsiType myType;
  private String myFieldName;
  private String mySetterName;
  private String myDefaultValue;
  private boolean myInsertSetter = true;
  private static final Logger LOG = Logger.getInstance("#" + ParameterData.class.getName());

  public ParameterData(String name, String parameterName, PsiType type) {
    myName = name;
    myParameterName = parameterName;
    myType = type;
  }

  public static void createFromConstructor(final PsiMethod constructor, final Map<String, ParameterData> result) {

    for (PsiParameter parameter : constructor.getParameterList().getParameters()) {
      initParameterData(parameter, result);
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
          final ParameterData parameterData = initParameterData(parameter, result);
          parameterData.setDefaultValue(args[i++].getText());
        }
      }
    }
  }

  private static ParameterData initParameterData(PsiParameter parameter, Map<String, ParameterData> result) {
    JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(parameter.getProject());
    final String paramName = parameter.getName();
    final String pureParamName = styleManager.variableNameToPropertyName(paramName, VariableKind.PARAMETER);

    ParameterData parameterData = result.get(pureParamName);
    if (parameterData == null) {
      parameterData = new ParameterData(pureParamName, paramName, parameter.getType());

      parameterData.setFieldName(styleManager.suggestVariableName(VariableKind.FIELD, pureParamName, null, parameter.getType()).names[0]);
      parameterData.setSetterName(PropertyUtil.suggestSetterName(pureParamName));

      result.put(pureParamName, parameterData);
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