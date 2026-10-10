// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteParameterCallHierarchyUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo,
                                                                                              ModPlanSafeDeleteUsage {

  private final SmartPsiElementPointer<PsiMethod> myCallerMethod;
  private final SmartPsiElementPointer<PsiParameter> myParameterInCaller;

  public SafeDeleteParameterCallHierarchyUsageInfo(PsiMethod calledMethod,
                                                   PsiParameter parameter,
                                                   PsiMethod callerMethod,
                                                   PsiParameter parameterInCaller) {
    super(calledMethod, parameter);
    myCallerMethod = SmartPointerManager.createPointer(callerMethod);
    myParameterInCaller = SmartPointerManager.createPointer(parameterInCaller);
  }

  @Override
  public @Nullable PsiParameter findReferencedElement() {
    return (PsiParameter)super.findReferencedElement();
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    final PsiParameter parameter = findReferencedElement();
    if (parameter != null && parameter.isValid()) {
      parameter.delete();
    }
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiParameter parameter = findReferencedElement();
    return parameter == null ? null : ModPlanEdit.delete(parameter);
  }

  public @Nullable PsiMethod getCalledMethod() {
    return (PsiMethod)getElement();
  }

  public @Nullable PsiMethod getCallerMethod() {
    return myCallerMethod.getElement();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    SafeDeleteParameterCallHierarchyUsageInfo info = (SafeDeleteParameterCallHierarchyUsageInfo)o;

    SmartPointerManager pointerManager = SmartPointerManager.getInstance(getProject());
    return pointerManager.pointToTheSameElement(myCallerMethod, info.myCallerMethod) &&
           pointerManager.pointToTheSameElement(myParameterInCaller, info.myParameterInCaller);
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myCallerMethod.hashCode();
    result = 31 * result + myParameterInCaller.hashCode();
    return result;
  }

  public @Nullable PsiParameter getParameterInCaller() {
    return myParameterInCaller.getElement();
  }
}
