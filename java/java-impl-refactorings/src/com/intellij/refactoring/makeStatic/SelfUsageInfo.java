// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.makeStatic;

import com.intellij.psi.PsiElement;

final class SelfUsageInfo extends InternalUsageInfo {
  SelfUsageInfo(PsiElement element, PsiElement referencedElement) {
    super(element, referencedElement);
  }
}
