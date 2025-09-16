// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiDocFragmentName;
import com.intellij.psi.javadoc.PsiDocFragmentRef;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiDocFragmentRefImpl extends CompositePsiElement implements PsiDocFragmentRef, Constants {
  public PsiDocFragmentRefImpl() {
    super(DOC_FRAGMENT_REF);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable PsiClass getScope() {
    return PsiDocMethodOrFieldRef.getScope(this);
  }

  @Override
  public @Nullable PsiDocFragmentName getFragmentName() {
    return PsiTreeUtil.getChildOfType(this, PsiDocFragmentName.class);
  }
}
