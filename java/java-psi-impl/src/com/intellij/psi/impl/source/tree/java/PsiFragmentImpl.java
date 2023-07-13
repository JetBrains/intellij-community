// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFragment;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public final class PsiFragmentImpl extends LeafPsiElement implements PsiFragment {

  public PsiFragmentImpl(@NotNull IElementType type, @NotNull CharSequence text) {
    super(type, text);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitFragment(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @Nullable Object getValue() {
    return null;
  }

  @Override
  public IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public boolean isTextBlock() {
    final IElementType token = getElementType();
    return token == JavaTokenType.TEXT_BLOCK_TEMPLATE_BEGIN ||
           token == JavaTokenType.TEXT_BLOCK_TEMPLATE_MID ||
           token == JavaTokenType.TEXT_BLOCK_TEMPLATE_END;
  }

  @Override
  public String toString(){
    return "PsiFragment:" + getElementType();
  }
}