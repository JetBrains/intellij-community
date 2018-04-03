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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.refactoring.util.LambdaRefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class UnwrapCall extends FixableUsageInfo {
  private final String myUnwrapMethod;

  public UnwrapCall(@NotNull PsiExpression call, @NotNull String unwrapMethod) {
    super(call);
    myUnwrapMethod = unwrapMethod;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiElement element = getElement();
    if (!(element instanceof PsiExpression)) return;
    if (element instanceof PsiMethodReferenceExpression) {
      PsiExpression expression = LambdaRefactoringUtil.convertToMethodCallInLambdaBody((PsiMethodReferenceExpression)element);
      if (expression == null) return;
      element = expression;
    }
    String newExpression = element.getText() + '.' + myUnwrapMethod + "()";
    MutationUtils.replaceExpression(newExpression, (PsiExpression)element);
  }
}
