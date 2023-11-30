// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.source.tree;

import com.intellij.psi.*;
import com.intellij.psi.impl.source.resolve.reference.ReferenceProvidersRegistry;
import com.intellij.psi.impl.source.tree.injected.CommentLiteralEscaper;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

public class PsiCommentImpl extends LeafPsiElement implements PsiComment, PsiLanguageInjectionHost {
  public PsiCommentImpl(@NotNull IElementType type, @NotNull CharSequence text) {
    super(type, text);
  }

  @Override
  public @NotNull IElementType getTokenType() {
    return getElementType();
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor){
    visitor.visitComment(this);
  }

  @Override
  public String toString(){
    return "PsiComment(" + getElementType().toString() + ")";
  }

  @Override
  public PsiReference @NotNull [] getReferences() {
    return ReferenceProvidersRegistry.getReferencesFromProviders(this);
  }

  @Override
  public boolean isValidHost() {
    return true;
  }

  @Override
  public PsiLanguageInjectionHost updateText(@NotNull String text) {
    return (PsiCommentImpl)replaceWithText(text);
  }

  @Override
  public @NotNull LiteralTextEscaper<PsiCommentImpl> createLiteralTextEscaper() {
    return new CommentLiteralEscaper(this);
  }
}
