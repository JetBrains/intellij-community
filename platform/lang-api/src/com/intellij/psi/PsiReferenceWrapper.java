package com.intellij.psi;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author traff
 */
public class PsiReferenceWrapper implements PsiReference{
  private final PsiReference myOriginalPsiReference;

  public PsiReferenceWrapper(PsiReference originalPsiReference) {
    myOriginalPsiReference = originalPsiReference;
  }

  @Override
  public PsiElement getElement() {
    return myOriginalPsiReference.getElement();
  }

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
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return myOriginalPsiReference.handleElementRename(newElementName);
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return myOriginalPsiReference.bindToElement(element);
  }

  @Override
  public boolean isReferenceTo(PsiElement element) {
    return myOriginalPsiReference.isReferenceTo(element);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return myOriginalPsiReference.getVariants();
  }

  @Override
  public boolean isSoft() {
    return myOriginalPsiReference.isSoft();
  }
}
