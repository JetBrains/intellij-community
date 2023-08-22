// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiTypeElement;
import com.intellij.psi.PsiUnnamedPattern;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import org.jetbrains.annotations.NotNull;


public class PsiUnnamedPatternImpl extends CompositePsiElement implements PsiUnnamedPattern, Constants {
  public PsiUnnamedPatternImpl() {
    super(UNNAMED_PATTERN);
  }

  @Override
  public @NotNull PsiTypeElement getTypeElement() {
    PsiTypeElement type = (PsiTypeElement)findPsiChildByType(JavaElementType.TYPE);
    assert type != null; // guaranteed by parser
    return type;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitUnnamedPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiUnnamedPattern";
  }
}

