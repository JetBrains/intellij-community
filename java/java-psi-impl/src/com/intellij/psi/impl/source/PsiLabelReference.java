// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiLabelReference implements PsiReference {
  private final PsiStatement myStatement;
  private PsiIdentifier myIdentifier;

  public PsiLabelReference(PsiStatement stat, PsiIdentifier identifier) {
    myStatement = stat;
    myIdentifier = identifier;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myStatement;
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    int start = myIdentifier.getStartOffsetInParent();
    return new TextRange(start, myIdentifier.getTextLength() + start);
  }

  @Override
  public PsiElement resolve() {
    String label = myIdentifier.getText();
    for (PsiElement context = myStatement; context != null; context = context.getContext()) {
      if (context instanceof PsiLabeledStatement && label.equals(((PsiLabeledStatement)context).getName())) {
        return context;
      }
    }
    return null;
  }

  @Override
  public @NotNull String getCanonicalText() {
    return getElement().getText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    myIdentifier = (PsiIdentifier)PsiImplUtil.setName(myIdentifier, newElementName);
    return myIdentifier;
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    if (!(element instanceof PsiLabeledStatement)) throw new IncorrectOperationException("Can't bind to non-labeled statement");
    return handleElementRename(((PsiLabeledStatement)element).getName());
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return resolve() == element;
  }

  @Override
  public String @NotNull [] getVariants() {
    return ArrayUtil.toStringArray(PsiImplUtil.findAllEnclosingLabels(myStatement));
  }

  @Override
  public boolean isSoft() {
    return false;
  }
}