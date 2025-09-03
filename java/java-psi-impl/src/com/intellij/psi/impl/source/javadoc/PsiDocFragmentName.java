// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiDocTagValue;
import org.jetbrains.annotations.NotNull;

public class PsiDocFragmentName extends CompositePsiElement implements PsiDocTagValue, Constants {
  public PsiDocFragmentName() {
    super(DOC_FRAGMENT_NAME);
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
}
