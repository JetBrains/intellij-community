// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source.javadoc;

import com.intellij.lang.ASTNode;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.SourceTreeToPsiMap;
import com.intellij.psi.impl.source.tree.CompositePsiElement;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.javadoc.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiSnippetDocTagImpl extends CompositePsiElement implements PsiSnippetDocTag {
  public PsiSnippetDocTagImpl() {
    super(JavaDocElementType.DOC_SNIPPET_TAG);
  }

  @Override
  public @NotNull String getName() {
    return getNameElement().getText().substring(1);
  }

  @Override
  public PsiDocComment getContainingComment() {
    ASTNode scope = getTreeParent();
    while (scope.getElementType() != JavaDocElementType.DOC_COMMENT) {
      scope = scope.getTreeParent();
    }
    return (PsiDocComment)SourceTreeToPsiMap.treeElementToPsi(scope);
  }

  @Override
  public PsiElement getNameElement() {
    return findPsiChildByType(JavaDocTokenType.DOC_TAG_NAME);
  }

  @Override
  public PsiElement @NotNull [] getDataElements() {
    return getChildrenAsPsiElements(PsiInlineDocTagImpl.VALUE_BIT_SET, PsiElement.ARRAY_FACTORY);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  @Override
  public @Nullable PsiSnippetDocTagValue getValueElement() {
    return (PsiSnippetDocTagValue)findPsiChildByType(JavaDocElementType.DOC_SNIPPET_TAG_VALUE);
  }

  @Override
  public String toString() {
    return "PsiSnippetDocTag";
  }
}
