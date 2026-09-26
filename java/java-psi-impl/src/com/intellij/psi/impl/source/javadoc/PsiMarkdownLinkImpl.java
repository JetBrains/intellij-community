// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.PsiMarkdownLink;

public final class PsiMarkdownLinkImpl extends CompositePsiElement implements PsiMarkdownLink {
  public PsiMarkdownLinkImpl() {
    super(JavaDocElementType.DOC_MARKDOWN_LINK);
  }
}
