// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiSnippetAttribute;
import com.intellij.psi.javadoc.PsiSnippetAttributeList;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;

public class PsiSnippetAttributeListImpl extends CompositePsiElement implements PsiSnippetAttributeList {
  public PsiSnippetAttributeListImpl() {
    super(JavaDocElementType.DOC_SNIPPET_ATTRIBUTE_LIST);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetAttributeList(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public PsiSnippetAttribute @NotNull [] getAttributes() {
    PsiSnippetAttribute[] children = PsiTreeUtil.getChildrenOfType(this, PsiSnippetAttribute.class);
    if (children == null) return PsiSnippetAttribute.EMPTY_ARRAY;
    return children;
  }

  @Override
  public String toString() {
    return "PsiSnippetAttributeList";
  }
}
