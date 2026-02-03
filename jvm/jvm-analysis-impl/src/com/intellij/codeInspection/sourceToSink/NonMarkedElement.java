// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.sourceToSink;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.uast.UCallExpression;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UReferenceExpression;
import org.jetbrains.uast.UResolvable;

public class NonMarkedElement {

  public final PsiModifierListOwner myNonMarked;
  public final PsiElement myRef;

  public final boolean myNext;

  NonMarkedElement(@NotNull PsiModifierListOwner marked, @NotNull PsiElement ref, boolean next) {
    myNonMarked = marked;
    myRef = ref;
    myNext = next;
  }

  static @Nullable NonMarkedElement create(@Nullable UElement uElement, boolean next) {
    if (!(uElement instanceof UCallExpression || uElement instanceof UReferenceExpression)) return null;
    UResolvable uResolvable = (UResolvable)uElement;
    PsiElement ref = uElement.getSourcePsi();
    if (ref == null) return null;
    PsiModifierListOwner target = ObjectUtils.tryCast(uResolvable.resolve(), PsiModifierListOwner.class);
    return target == null ? null : new NonMarkedElement(target, ref, next);
  }
}
