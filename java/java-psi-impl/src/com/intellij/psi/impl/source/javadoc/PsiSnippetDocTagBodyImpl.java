// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import org.jetbrains.annotations.NotNull;

public class PsiSnippetDocTagBodyImpl extends CompositePsiElement implements PsiSnippetDocTagBody {
  public PsiSnippetDocTagBodyImpl() {
    super(JavaDocElementType.DOC_SNIPPET_BODY);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetDocTagBody(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTagBody";
  }

  @Override
  public PsiElement @NotNull [] getContent() {
    return getChildrenAsPsiElements(JavaDocTokenType.DOC_COMMENT_DATA, ARRAY_FACTORY);
  }
}
