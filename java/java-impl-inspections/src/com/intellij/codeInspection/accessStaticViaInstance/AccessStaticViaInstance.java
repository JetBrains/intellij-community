// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.accessStaticViaInstance;

import com.intellij.codeInsight.daemon.impl.quickfix.AccessStaticViaInstanceFix;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiReferenceExpression;

public class AccessStaticViaInstance extends AccessStaticViaInstanceBase {
  @Override
  protected LocalQuickFix createAccessStaticViaInstanceFix(PsiReferenceExpression expr,
                                                           JavaResolveResult result) {
    return LocalQuickFix.from(new AccessStaticViaInstanceFix(expr, result));
  }
}
