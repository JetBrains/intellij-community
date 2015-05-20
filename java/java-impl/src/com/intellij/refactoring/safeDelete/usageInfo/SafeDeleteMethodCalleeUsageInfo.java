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
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.util.IncorrectOperationException;

public class SafeDeleteMethodCalleeUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {

  private final PsiMethod myCalledMethod;
  private final PsiMethod myCallerMethod;

  public SafeDeleteMethodCalleeUsageInfo(PsiMethod calledMethod, PsiMethod callerMethod) {
    super(calledMethod, calledMethod);
    myCalledMethod = calledMethod;
    myCallerMethod = callerMethod;
  }

  public void performRefactoring() throws IncorrectOperationException {
    final PsiMethod callee = myCalledMethod;
    if (callee != null && callee.isValid()) {
      callee.delete();
    }
  }

  public PsiMethod getCalledMethod() {
    return myCalledMethod;
  }

  public PsiMethod getCallerMethod() {
    return myCallerMethod;
  }
}
