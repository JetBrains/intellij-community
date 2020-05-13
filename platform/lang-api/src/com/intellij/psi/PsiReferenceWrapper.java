// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class PsiReferenceWrapper implements PsiReference {
  private final PsiReference myOriginalPsiReference;

  public PsiReferenceWrapper(PsiReference originalPsiReference) {
    myOriginalPsiReference = originalPsiReference;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myOriginalPsiReference.getElement();
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myOriginalPsiReference.getRangeInElement();
  }

  @Override
  public PsiElement resolve() {
    return myOriginalPsiReference.resolve();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
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
