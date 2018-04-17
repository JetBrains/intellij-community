package com.intellij.psi.impl.source.resolve.reference.impl;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Sergey Evdokimov
 */
public class PsiDelegateReference implements PsiReference {

  private final PsiReference myDelegate;

  public PsiDelegateReference(@NotNull PsiReference delegate) {
    myDelegate = delegate;
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return myDelegate.getElement();
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return myDelegate.getRangeInElement();
  }

  @Nullable
  @Override
  public PsiElement resolve() {
    return myDelegate.resolve();
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    return myDelegate.getCanonicalText();
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myDelegate.handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myDelegate.bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return myDelegate.isReferenceTo(element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return myDelegate.getVariants();
  }

  @Override
  public boolean isSoft() {
    return myDelegate.isSoft();
  }

  public static PsiReference createSoft(PsiReference origin, final boolean soft) {
    return new PsiDelegateReference(origin) {
      @Override
      public boolean isSoft() {
        return soft;
      }
    };
  }
}
