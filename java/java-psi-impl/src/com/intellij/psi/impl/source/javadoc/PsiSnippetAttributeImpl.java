// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PsiSnippetAttributeImpl extends CompositePsiElement implements PsiSnippetAttribute {
  public PsiSnippetAttributeImpl() {
    super(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE);
  }

  @Override
  public @NotNull PsiElement getNameIdentifier() {
    return Objects.requireNonNull(findPsiChildByType(JavaDocTokenType.DOC_TAG_ATTRIBUTE_NAME));
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetAttribute(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String getName() {
    return getNameIdentifier().getText();
  }

  @Override
  public @Nullable PsiElement getValue() {
    return findPsiChildByType(JavaDocTokenType.DOC_TAG_ATTRIBUTE_VALUE);
  }

  @Override
  public String toString() {
    return "PsiSnippetAttribute:" + getName();
  }
}
