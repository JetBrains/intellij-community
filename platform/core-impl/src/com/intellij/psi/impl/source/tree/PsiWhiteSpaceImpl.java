// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.lang.Language;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.TokenType;
import org.jetbrains.annotations.NotNull;

public class PsiWhiteSpaceImpl extends LeafPsiElement implements PsiWhiteSpace {
  public PsiWhiteSpaceImpl(CharSequence text) {
    super(TokenType.WHITE_SPACE, text);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitWhiteSpace(this);
  }

  @Override
  public String toString(){
    return "PsiWhiteSpace";
  }

  @Override
  public @NotNull Language getLanguage() {
    PsiElement master = getParent();
    return master != null ?  master.getLanguage() : Language.ANY;
  }
}
