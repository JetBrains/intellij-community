package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.util.Function;

/**
* @author max
*/
public class ReferenceDescriptor {
  public static final Function<PsiReference, ReferenceDescriptor> MAPPER = new Function<PsiReference, ReferenceDescriptor>() {
    public ReferenceDescriptor fun(PsiReference psiReference) {
      final PsiElement element = psiReference.getElement();
      final PsiFile file = element.getContainingFile();
      return new ReferenceDescriptor(file, element.getTextRange().getStartOffset() + psiReference.getRangeInElement().getStartOffset());
    }
  };
  private final PsiFile file;
  private final int offset;

  ReferenceDescriptor(PsiFile file, int offset) {
    this.file = file;
    this.offset = offset;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ReferenceDescriptor)) return false;

    ReferenceDescriptor that = (ReferenceDescriptor)o;

    if (offset != that.offset) return false;
    return file.equals(that.file);
  }

  @Override
  public int hashCode() {
    return 31 * file.hashCode() + offset;
  }
}
