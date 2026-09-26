// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.paths;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;

final class WebReferenceVetoRenameCondition implements Condition<PsiElement> {

  @Override
  public boolean value(PsiElement element) {
    return element instanceof WebReference.MyFakePsiElement;
  }
}
