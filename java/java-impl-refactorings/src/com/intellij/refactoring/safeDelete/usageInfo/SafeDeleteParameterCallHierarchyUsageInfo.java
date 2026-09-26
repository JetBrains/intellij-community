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

public class SafeDeleteParameterCallHierarchyUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo {

  private final PsiMethod myCalledMethod;
  private final PsiMethod myCallerMethod;
  private final PsiParameter myParameterInCaller;

  public SafeDeleteParameterCallHierarchyUsageInfo(PsiMethod calledMethod,
                                                   PsiParameter parameter,
                                                   PsiMethod callerMethod,
                                                   PsiParameter parameterInCaller) {
    super(calledMethod, parameter);
    myCalledMethod = calledMethod;
    myCallerMethod = callerMethod;
    myParameterInCaller = parameterInCaller;
  }

  @Override
  public PsiParameter getReferencedElement() {
    return (PsiParameter)super.getReferencedElement();
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    final PsiParameter parameter = getReferencedElement();
    if (parameter != null && parameter.isValid()) {
      parameter.delete();
    }
  }

  public PsiMethod getCalledMethod() {
    return myCalledMethod;
  }

  public PsiMethod getCallerMethod() {
    return myCallerMethod;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SafeDeleteParameterCallHierarchyUsageInfo info = (SafeDeleteParameterCallHierarchyUsageInfo)o;

    if (!myCalledMethod.equals(info.myCalledMethod)) return false;
    if (!myCallerMethod.equals(info.myCallerMethod)) return false;
    if (!myParameterInCaller.equals(info.myParameterInCaller)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myCalledMethod.hashCode();
    result = 31 * result + myCallerMethod.hashCode();
    result = 31 * result + myParameterInCaller.hashCode();
    return result;
  }

  public PsiParameter getParameterInCaller() {
    return myParameterInCaller;
  }
}
