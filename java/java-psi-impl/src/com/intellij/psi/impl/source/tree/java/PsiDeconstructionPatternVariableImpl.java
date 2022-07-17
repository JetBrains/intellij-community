// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

import static com.intellij.psi.impl.source.tree.JavaElementType.DECONSTRUCTION_PATTERN_VARIABLE;

public class PsiDeconstructionPatternVariableImpl extends CompositePsiElement implements PsiPatternVariable {
  public PsiDeconstructionPatternVariableImpl() {
    super(DECONSTRUCTION_PATTERN_VARIABLE);
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

  @Override
  public String toString() {
    return "PsiDeconstructionPatternVariable";
  }

  @Override
  public @Nullable PsiModifierList getModifierList() {
    return null;
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    return false;
  }

  @Override
  public @NotNull PsiElement getDeclarationScope() {
    return JavaSharedImplUtil.getPatternVariableDeclarationScope(this);
  }

  @Override
  public boolean isVarArgs() {
    return false;
  }

  @Override
  public @NotNull PsiType getType() {
    return JavaSharedImplUtil.getType(getTypeElement(), getNameIdentifier());
  }

  @Override
  public @NotNull PsiTypeElement getTypeElement() {
    return getPattern().getTypeElement();
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
  public void normalizeDeclaration() throws IncorrectOperationException {

  }

  @Override
  public @Nullable Object computeConstantValue() {
    return null;
  }

  @Override
  public @NotNull PsiIdentifier getNameIdentifier() {
    return (PsiIdentifier)Objects.requireNonNull(findPsiChildByType(JavaTokenType.IDENTIFIER));
  }

  @Override
  public @NotNull String getName() {
    return getNameIdentifier().getText();
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    PsiImplUtil.setName(getNameIdentifier(), name);
    return this;
  }

  @Override
  public @NotNull PsiDeconstructionPattern getPattern() {
    return (PsiDeconstructionPattern)getParent();
  }
}
