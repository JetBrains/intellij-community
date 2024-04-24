// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiReceiverParameterImpl extends CompositePsiElement implements PsiReceiverParameter {
  public PsiReceiverParameterImpl() {
    super(JavaElementType.RECEIVER_PARAMETER);
  }

  @Override
  public @NotNull PsiThisExpression getIdentifier() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiThisExpression.class);
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return PsiTreeUtil.getChildOfType(this, PsiModifierList.class);
  }

  @Override
  public boolean hasModifierProperty(@PsiModifier.ModifierConstant @NotNull String name) {
    PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @Override
  public @NotNull PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getIdentifier());
  }

  @Override
  public @NotNull PsiTypeElement getTypeElement() {
    return PsiTreeUtil.getRequiredChildOfType(this, PsiTypeElement.class);
  }

  @Override
  public @Nullable PsiExpression getInitializer() {
    return null;
  }

  @Override
  public boolean hasInitializer() {
    return false;
  }

  @Override
  public @Nullable PsiIdentifier getNameIdentifier() {
    return null;
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Cannot rename receiver parameter");
  }

  @Override
  public void normalizeDeclaration() throws IncorrectOperationException { }

  @Override
  public @Nullable Object computeConstantValue() {
    return null;
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitReceiverParameter(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public int getTextOffset() {
    return getIdentifier().getTextOffset();
  }

  @Override
  public String toString() {
    return "PsiReceiverParameter";
  }
}
