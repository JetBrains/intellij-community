// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.openapi.diagnostic.Attachment;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.Constants;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaElementType;
import com.intellij.psi.impl.source.tree.JavaSharedImplUtil;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
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
    PsiIdentifier identifier = PsiTreeUtil.getChildOfType(this, PsiIdentifier.class);
    if (identifier == null) {
      PsiFile file = getContainingFile();
      Logger.getInstance(PsiPatternVariableImpl.class).error("Pattern without identifier", new Attachment("File content", file.getText()));
    }
    return Objects.requireNonNull(identifier);
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

  @NotNull
  @Override
  public PsiPattern getPattern() {
    return (PsiPattern)getParent();
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
  public PsiElement getDeclarationScope() {
    return JavaSharedImplUtil.getPatternVariableDeclarationScope(this);
  }

  @Override
  public boolean isVarArgs() {
    return false;
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
    return (PsiModifierList)findPsiChildByType(JavaElementType.MODIFIER_LIST);
  }

  @Override
  public boolean hasModifierProperty(@NotNull String name) {
    final PsiModifierList modifierList = getModifierList();
    return modifierList != null && modifierList.hasModifierProperty(name);
  }

  @NotNull
  @Override
  public SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public void delete() throws IncorrectOperationException {
    PsiPattern pattern = getPattern();
    if (pattern instanceof PsiTypeTestPattern) {
      if (pattern.getParent() instanceof PsiInstanceOfExpression) {
        pattern.replace(getTypeElement());
        return;
      }
    }
    super.delete();
  }

  @Override
  public String toString() {
    return "PsiPatternVariable:" + getName();
  }
}

