// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete.usageInfo;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsage;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;

public class SafeDeleteMemberCalleeUsageInfo extends SafeDeleteUsageInfo implements SafeDeleteCustomUsageInfo, ModPlanSafeDeleteUsage {

  private final SmartPsiElementPointer<PsiMember> myCallerMember;

  public SafeDeleteMemberCalleeUsageInfo(PsiElement calledElement, PsiMember callerMember) {
    super(calledElement, calledElement);
    myCallerMember = SmartPointerManager.createPointer(callerMember);
  }

  @Override
  public void performRefactoring() throws IncorrectOperationException {
    final PsiElement callee = getElement();
    if (callee != null && callee.isValid()) {
      callee.delete();
    }
  }

  public @Nullable PsiElement getCalledElement() {
    return getElement();
  }

  public @Nullable PsiMember getCallerMember() {
    return myCallerMember.getElement();
  }

  @Override
  public @Nullable ModPlanEdit asModPlanEdit() {
    PsiElement callee = getElement();
    return callee == null ? null : ModPlanEdit.delete(callee);
  }
}
