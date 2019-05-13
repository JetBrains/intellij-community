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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiTypeElement;
import com.intellij.refactoring.psi.MutationUtils;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class ChangeReturnType extends FixableUsageInfo {
  private final PsiMethod myMethod;
  private final String myType;

  public ChangeReturnType(@NotNull PsiMethod method, @NotNull String type) {
    super(method);
    myMethod = method;
    myType = type;
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    PsiTypeElement returnType = myMethod.getReturnTypeElement();
    assert returnType != null : myMethod;
    MutationUtils.replaceType(myType, returnType);
  }
}
