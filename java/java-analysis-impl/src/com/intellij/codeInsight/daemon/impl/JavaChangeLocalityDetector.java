// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.codeInsight.daemon.impl;

import com.intellij.codeInsight.daemon.ChangeLocalityDetector;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class JavaChangeLocalityDetector implements ChangeLocalityDetector {
  @Override
  public @Nullable PsiElement getChangeHighlightingDirtyScopeFor(final @NotNull PsiElement element) {
    // optimization
    PsiElement parent = element.getParent();
    PsiElement grand;
    if (element instanceof PsiCodeBlock
        && parent instanceof PsiMethod
        && !((PsiMethod)parent).isConstructor()
        && (grand = parent.getParent()) instanceof PsiClass
        && !(grand instanceof PsiAnonymousClass)
        && !HighlightingPsiUtil.hasReferenceInside(element) // turn off optimization when a reference was changed to avoid "unused symbol" false positives
    ) {
      // for changes inside method, rehighlight codeblock only
      // do not use this optimization for constructors and class initializers - to update non-initialized fields
      return parent;
    }
    return null;
  }
}