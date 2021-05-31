// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiNullCaseLabelElement;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import org.jetbrains.annotations.NotNull;

public class PsiNullCaseLabelElementImpl extends CompositePsiElement implements PsiNullCaseLabelElement, Constants {
  public PsiNullCaseLabelElementImpl() {
    super(NULL_CASE_LABEL_ELEMENT);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitNullElement(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiNullCaseLabelElementImpl";
  }
}

