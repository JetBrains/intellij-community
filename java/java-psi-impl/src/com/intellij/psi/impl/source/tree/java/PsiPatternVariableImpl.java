// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PsiPatternVariableImpl extends CompositePsiElement implements PsiPatternVariable, Constants {
  public PsiPatternVariableImpl() {
    super(PATTERN_VARIABLE);
  }

  @Override
  public PsiIdentifier setName(@NotNull String name) throws IncorrectOperationException {
    PsiIdentifier identifier = getNameIdentifier();
    return (PsiIdentifier)PsiImplUtil.setName(identifier, name);
  }

  @Override
  @NotNull
  public PsiIdentifier getNameIdentifier() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiIdentifier.class));
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitPatternVariable(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Nullable
  @Override
  public PsiPattern getPattern() {
    return ObjectUtils.tryCast(getParent(), PsiPattern.class);
  }

  @Override
  public void setInitializer(@Nullable PsiExpression initializer) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException {
  }

  @Nullable
  @Override
  public Object computeConstantValue() {
    return null;
  }

  @NotNull
  @Override
  public PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @NotNull
  @Override
  public PsiTypeElement getTypeElement() {
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiTypeElement.class));
  }

  @Nullable
  @Override
  public PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @NotNull
  @Override
  public String getName() {
    PsiIdentifier identifier = getNameIdentifier();
    return identifier.getText();
  }

  @Override
  public int getTextOffset() {
    return getNameIdentifier().getTextOffset();
  }

  @Nullable
  @Override
  public PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    PsiPattern pattern = getPattern();
    if (pattern != null) {
      PsiElement parent = pattern.getParent();
      while (parent instanceof PsiInstanceOfExpression || parent instanceof PsiParenthesizedExpression ||
             parent instanceof PsiConditionalExpression ||
             parent instanceof PsiPrefixExpression && ((PsiPrefixExpression)parent).getOperationTokenType().equals(EXCL) ||
             parent instanceof PsiPolyadicExpression && 
             (((PsiPolyadicExpression)parent).getOperationTokenType().equals(ANDAND) || 
              ((PsiPolyadicExpression)parent).getOperationTokenType().equals(OROR))) {
        parent = parent.getParent();
      }
      if (parent instanceof PsiIfStatement || parent instanceof PsiConditionalLoopStatement) {
        return new LocalSearchScope(parent.getParent());
      }
      return new LocalSearchScope(parent);
    }
    return super.getUseScope();
  }

  @Override
  public String toString() {
    return "PsiPatternVariable:" + getName();
  }
}

