/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.wrapreturnvalue.usageInfo;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class ReturnWrappedValue extends FixableUsageInfo {
  private final PsiReturnStatement myStatement;

  public ReturnWrappedValue(PsiReturnStatement statement) {
    super(statement);
    myStatement = statement;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiMethodCallExpression returnValue = (PsiMethodCallExpression)myStatement.getReturnValue();
    assert returnValue != null;
    PsiExpression qualifier = returnValue.getMethodExpression().getQualifierExpression();
    assert qualifier != null;
    MutationUtils.replaceExpression(qualifier.getText(), returnValue);
  }
}
