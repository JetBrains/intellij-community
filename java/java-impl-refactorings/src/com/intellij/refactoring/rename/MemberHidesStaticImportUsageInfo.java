// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.refactoring.util.RelatedUsageInfo;


public class MemberHidesStaticImportUsageInfo extends RelatedUsageInfo {
  public MemberHidesStaticImportUsageInfo(PsiElement element, PsiElement referencedElement, PsiElement collidingElement) {
    super(element, null, referencedElement, collidingElement);
  }
}
