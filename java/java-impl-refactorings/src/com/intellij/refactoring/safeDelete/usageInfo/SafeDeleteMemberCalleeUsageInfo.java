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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.util.IncorrectOperationException;

public class SafeDeleteMemberCalleeUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {

  private final PsiElement myCalledElement;
  private final PsiMember myCallerMember;

  public SafeDeleteMemberCalleeUsageInfo(PsiElement calledElement, PsiMember callerMember) {
    super(calledElement, calledElement);
    myCalledElement = calledElement;
    myCallerMember = callerMember;
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    final PsiElement callee = myCalledElement;
    if (callee != null && callee.isValid()) {
      callee.delete();
    }
  }

  public PsiElement getCalledElement() {
    return myCalledElement;
  }

  public PsiMember getCallerMember() {
    return myCallerMember;
  }
}
