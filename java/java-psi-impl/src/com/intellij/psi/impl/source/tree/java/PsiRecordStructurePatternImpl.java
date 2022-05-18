// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiPattern;
import com.intellij.psi.PsiRecordStructurePattern;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.impl.source.tree.JavaElementType.RECORD_STRUCTURE_PATTERN;

public class PsiRecordStructurePatternImpl extends CompositePsiElement implements PsiRecordStructurePattern {
  public PsiRecordStructurePatternImpl() {
    super(RECORD_STRUCTURE_PATTERN);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitRecordStructurePattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiPattern @NotNull [] getRecordComponents() {
    PsiPattern[] children = PsiTreeUtil.getChildrenOfType(this, PsiPattern.class);
    if (children == null) {
      return PsiPattern.EMPTY;
    }
    return children;
  }

  @Override
  public String toString() {
    return "PsiRecordStructurePattern";
  }
}
