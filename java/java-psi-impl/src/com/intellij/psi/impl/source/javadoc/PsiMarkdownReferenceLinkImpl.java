// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.javadoc.PsiMarkdownReferenceLink;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.impl.source.tree.JavaDocElementType.DOC_MARKDOWN_REFERENCE_LINK;

public class PsiMarkdownReferenceLinkImpl extends CompositePsiElement implements PsiMarkdownReferenceLink {
  public PsiMarkdownReferenceLinkImpl() {
    super(DOC_MARKDOWN_REFERENCE_LINK);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitMarkdownReferenceLink(this);
      return;
    }
    super.accept(visitor);
  }

  @Override
  public String toString() {
    return "PsiReferenceLink:";
  }

  @Override
  public @Nullable PsiElement getLabel() {
    if (getChildren().length >= 1) return getChildren()[1];
    // malformed/incomplete link
    return null;
  }

  @Override
  public boolean isShortLink() {
    return getChildren().length < 4;
  }

  @Override
  public @Nullable PsiElement getLinkElement() {
    if (isShortLink()) return getLabel();
    return getChildren()[4];
  }
}
