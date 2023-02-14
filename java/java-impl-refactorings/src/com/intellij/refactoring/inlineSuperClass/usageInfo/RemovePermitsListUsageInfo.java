// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.inlineSuperClass.usageInfo;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeletePermitsClassUsageInfo;
import com.intellij.refactoring.util.FixableUsageInfo;
import com.intellij.util.IncorrectOperationException;

public class RemovePermitsListUsageInfo extends FixableUsageInfo {
  private final SafeDeletePermitsClassUsageInfo mySafeDeletePermitsClassUsageInfo;

  public RemovePermitsListUsageInfo(PsiJavaCodeReferenceElement reference, PsiClass refClass, PsiClass parentClass) {
    super(reference);
    mySafeDeletePermitsClassUsageInfo = new SafeDeletePermitsClassUsageInfo(reference, refClass, parentClass, false);
  }

  @Override
  public void fixUsage() throws IncorrectOperationException {
    if (mySafeDeletePermitsClassUsageInfo.isSafeDelete()) {
      mySafeDeletePermitsClassUsageInfo.deleteElement();
    }
  }
}
