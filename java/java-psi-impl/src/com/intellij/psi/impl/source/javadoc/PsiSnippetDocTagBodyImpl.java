// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiSnippetDocTagBody;

public class PsiSnippetDocTagBodyImpl extends CompositePsiElement implements PsiSnippetDocTagBody {
  public PsiSnippetDocTagBodyImpl() {
    super(JavaDocElementType.DOC_SNIPPET_BODY);
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTagBody";
  }
}
