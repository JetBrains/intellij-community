// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiSnippetAttributeList;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;
import com.intellij.psi.javadoc.PsiSnippetDocTagValue;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PsiSnippetDocTagValueImpl extends CompositePsiElement implements PsiSnippetDocTagValue {
  public PsiSnippetDocTagValueImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public void accept(@NotNull PsiElementVisitor visitor) {
    super.accept(visitor);
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitSnippetDocTagValue(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  @Override
  public @NotNull PsiSnippetAttributeList getAttributeList() {
    // always present but may be zero length
    return Objects.requireNonNull(PsiTreeUtil.getChildOfType(this, PsiSnippetAttributeList.class));
  }

  @Override
  public @Nullable PsiSnippetDocTagBody getBody() {
    return PsiTreeUtil.getChildOfType(this, PsiSnippetDocTagBody.class);
  }

  @Override
  public String toString(){
    return "PsiSnippetDocTagValue";
  }
}
