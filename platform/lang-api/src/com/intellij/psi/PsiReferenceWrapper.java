// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiReferenceWrapper implements PsiReference {
  private final PsiReference myOriginalPsiReference;

  public PsiReferenceWrapper(PsiReference originalPsiReference) {
    myOriginalPsiReference = originalPsiReference;
  }

  @Override
  public @NotNull PsiElement getElement() {
    return myOriginalPsiReference.getElement();
  }

  @Override
  public @NotNull TextRange getRangeInElement() {
    return myOriginalPsiReference.getRangeInElement();
  }

  @Override
  public PsiElement resolve() {
    return myOriginalPsiReference.resolve();
  }

  @Override
  public @NotNull String getCanonicalText() {
    return myOriginalPsiReference.getCanonicalText();
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    return myOriginalPsiReference.handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myOriginalPsiReference.bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myOriginalPsiReference.isReferenceTo(element);
  }

  @Override
  public Object @NotNull [] getVariants() {
    return myOriginalPsiReference.getVariants();
  }

  @Override
  public boolean isSoft() {
    return myOriginalPsiReference.isSoft();
  }

  public <T extends PsiReference> boolean isInstance(Class<T> clazz) {
    if (myOriginalPsiReference instanceof PsiReferenceWrapper) {
      return ((PsiReferenceWrapper)myOriginalPsiReference).isInstance(clazz);
    }
    return clazz.isInstance(myOriginalPsiReference);
  }

  public <T extends PsiReference> T cast(Class<T> clazz) {
    if (myOriginalPsiReference instanceof PsiReferenceWrapper) {
      return ((PsiReferenceWrapper)myOriginalPsiReference).cast(clazz);
    }
    return clazz.cast(myOriginalPsiReference);
  }
}
