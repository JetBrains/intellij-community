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
 * Date: 02-Feb-2009
 */
package com.intellij.refactoring.replaceConstructorWithBuilder.usageInfo;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;

public class ReplaceConstructorWithSettersChainInfo extends FixableUsageInfo {
  private final String  myBuilderClass;
  private final Map<String, ParameterData> myParametersMap;

  public ReplaceConstructorWithSettersChainInfo(PsiNewExpression constructorReference, String builderClass, Map<String, ParameterData> parametersMap) {
    super(constructorReference);
    myBuilderClass = builderClass;
    myParametersMap = parametersMap;
  }

  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression expr = (PsiNewExpression)getElement();
    if (expr != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(expr.getProject()).getElementFactory();
      final PsiMethod constructor = expr.resolveConstructor();
      if (constructor != null) {
        StringBuffer buf = new StringBuffer();
        final PsiExpressionList argumentList = expr.getArgumentList();
        if (argumentList != null) {
          final PsiExpression[] args = argumentList.getExpressions();
          final PsiParameter[] parameters = constructor.getParameterList().getParameters();

          final JavaCodeStyleManager styleManager = JavaCodeStyleManager.getInstance(constructor.getProject());
          for (int i = 0; i < Math.min(constructor.getParameterList().getParametersCount(), args.length); i++) {
            String arg = args[i].getText();
            if (parameters[i].isVarArgs()) {
              for(int ia = i + 1; ia < args.length; ia++) {
                arg += ", " + args[ia].getText();
              }
            }

            final String pureParamName = styleManager.variableNameToPropertyName(parameters[i].getName(), VariableKind.PARAMETER);
            final ParameterData data = myParametersMap.get(pureParamName);
            if (!Comparing.strEqual(arg, data.getDefaultValue()) || data.isInsertSetter()) {
              buf.append(data.getSetterName()).append("(").append(arg).append(").");
            }
          }

          final PsiExpression settersChain = elementFactory.createExpressionFromText(
            "new " + myBuilderClass + "()." + buf.toString() + "create" + StringUtil.capitalize(constructor.getName()) + "()",
            null);

          styleManager.shortenClassReferences(expr.replace(settersChain));
        }
      }
    }
  }
}