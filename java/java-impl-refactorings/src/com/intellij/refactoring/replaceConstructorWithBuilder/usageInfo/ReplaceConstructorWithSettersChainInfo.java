// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.replaceConstructorWithBuilder.usageInfo;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import com.intellij.refactoring.replaceConstructorWithBuilder.ParameterData;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

import java.util.Map;
import java.util.Objects;

public class ReplaceConstructorWithSettersChainInfo extends FixableUsageInfo {
  private final String  myBuilderClass;
  private final Map<String, ParameterData> myParametersMap;

  public ReplaceConstructorWithSettersChainInfo(PsiNewExpression constructorReference, String builderClass, Map<String, ParameterData> parametersMap) {
    super(constructorReference);
    myBuilderClass = builderClass;
    myParametersMap = parametersMap;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    final PsiNewExpression expr = (PsiNewExpression)getElement();
    if (expr != null) {
      final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(expr.getProject());
      final PsiMethod constructor = expr.resolveConstructor();
      if (constructor != null) {
        StringBuilder buf = new StringBuilder();
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
          PsiNewExpression expression = (PsiNewExpression)PsiDiamondTypeUtil.expandTopLevelDiamondsInside(expr);
          PsiReferenceParameterList typeArguments = Objects.requireNonNull(expression.getClassReference()).getParameterList();
          assert typeArguments != null;
          final PsiExpression settersChain = elementFactory.createExpressionFromText(
            "new " + myBuilderClass + typeArguments.getText() + "()." + buf + "create" + StringUtil.capitalize(constructor.getName()) + "()",
            null);

          styleManager.shortenClassReferences(expr.replace(settersChain));
        }
      }
    }
  }
}