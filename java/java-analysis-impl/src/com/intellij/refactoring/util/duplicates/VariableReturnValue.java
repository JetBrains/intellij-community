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
package com.intellij.refactoring.util.duplicates;

import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;

/**
 * @author dsl
 */
public class VariableReturnValue implements ReturnValue {
  private final PsiVariable myVariable;

  public VariableReturnValue(PsiVariable variable) {
    myVariable = variable;
  }

  public boolean isEquivalent(ReturnValue other) {
    if (!(other instanceof VariableReturnValue)) return false;
    return myVariable == ((VariableReturnValue)other).myVariable;
  }

  public PsiVariable getVariable() {
    return myVariable;
  }

  public PsiStatement createReplacement(final PsiMethod extractedMethod, final PsiMethodCallExpression methodCallExpression) throws IncorrectOperationException {
    final PsiDeclarationStatement statement;

    final PsiElementFactory elementFactory = JavaPsiFacade.getInstance(methodCallExpression.getProject()).getElementFactory();
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(methodCallExpression.getProject());
    statement = (PsiDeclarationStatement)styleManager.reformat(
      elementFactory.createVariableDeclarationStatement(myVariable.getName(), myVariable.getType(), methodCallExpression)
    );
    ((PsiVariable)statement.getDeclaredElements()[0]).getModifierList().replace(myVariable.getModifierList());
    return statement;
  }
}
