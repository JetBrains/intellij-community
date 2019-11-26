// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiTypeTestPatternImpl extends CompositePsiElement implements PsiTypeTestPattern, Constants {
  public PsiTypeTestPatternImpl() {
    super(TYPE_TEST_PATTERN);
  }


  @Override
  public PsiTypeElement getCheckType() {
    return PsiTreeUtil.getChildOfType(this, PsiTypeElement.class);
  }

  @Override
  public PsiIdentifier setName(@NotNull String name) throws IncorrectOperationException {
    PsiIdentifier identifier = getNameIdentifier();
    if (identifier == null) throw new IncorrectOperationException();
    return (PsiIdentifier)PsiImplUtil.setName(identifier, name);
  }

  @Override
  @Nullable
  public PsiIdentifier getNameIdentifier() {
    return PsiTreeUtil.getChildOfType(this, PsiIdentifier.class);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitTypeTestPattern(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PsiTypeTestPattern");
    String name = getName();
    if (name != null) {
      sb.append(':');
      sb.append(getText());
    }
    return sb.toString();
  }

  @Override
  public String getName() {
    PsiIdentifier identifier = getNameIdentifier();
    if (identifier == null) return null;
    return identifier.getText();
  }
}

