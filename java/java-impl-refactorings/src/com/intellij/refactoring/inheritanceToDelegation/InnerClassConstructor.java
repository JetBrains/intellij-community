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
package com.intellij.refactoring.inheritanceToDelegation;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class InnerClassConstructor extends InnerClassMethod {
  private static final Logger LOG = Logger.getInstance(InnerClassConstructor.class);
  public InnerClassConstructor(PsiMethod method) {
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
