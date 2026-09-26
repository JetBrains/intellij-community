// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiExpressionList;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.intellij.util.IncorrectOperationException;

final class InnerClassConstructor extends InnerClassMethod {
  private static final Logger LOG = Logger.getInstance(InnerClassConstructor.class);
  InnerClassConstructor(PsiMethod method) {
    super(method);
    LOG.assertTrue(method.isConstructor());
  }

  @Override
  public void createMethod(PsiClass innerClass) throws IncorrectOperationException {
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(innerClass.getProject());
    final PsiMethod constructor = factory.createConstructor();
    constructor.getNameIdentifier().replace(innerClass.getNameIdentifier());
    final PsiParameterList parameterList = myMethod.getParameterList();
    constructor.getParameterList().replace(parameterList);
    PsiExpressionStatement superCallStatement =
            (PsiExpressionStatement) factory.createStatementFromText("super();", null);

    PsiExpressionList arguments = ((PsiMethodCallExpression) superCallStatement.getExpression()).getArgumentList();
    PsiParameter[] parameters = parameterList.getParameters();
    for (PsiParameter parameter : parameters) {
      arguments.add(factory.createExpressionFromText(parameter.getName(), null));
    }
    constructor.getBody().add(superCallStatement);
    innerClass.add(constructor);
  }
}
