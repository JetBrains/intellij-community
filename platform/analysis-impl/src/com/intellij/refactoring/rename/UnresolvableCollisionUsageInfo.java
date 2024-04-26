// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.rename;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiElement;

public abstract class UnresolvableCollisionUsageInfo extends CollisionUsageInfo {
  public UnresolvableCollisionUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }

  public abstract @NlsContexts.DialogMessage String getDescription();
}
