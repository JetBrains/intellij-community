package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author ven
 */
public class PsiCapturedWildcardType extends PsiType {
  private PsiWildcardType myExistential;

  private PsiCapturedWildcardType(PsiWildcardType existential) {
    myExistential = existential;
  }

  public static PsiCapturedWildcardType create (PsiWildcardType existential) {
    return new PsiCapturedWildcardType(existential);
  }

  public String getPresentableText() {
    return myExistential.getPresentableText();
  }

  public String getCanonicalText() {
    return myExistential.getCanonicalText();
  }

  public String getInternalCanonicalText() {
    return "capture<" + myExistential.getInternalCanonicalText() + '>';
  }

  public boolean isValid() {
    return myExistential.isValid();
  }

  public boolean equalsToText(String text) {
    return false;
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitCapturedWildcardType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return myExistential.getResolveScope();
  }

  public PsiType[] getSuperTypes() {
    return myExistential.getSuperTypes();
  }

  //equals() is not implemented intentionally

  public PsiType getLowerBound () {
    return myExistential.isSuper() ? myExistential.getBound() : PsiType.NULL;
  }

  public PsiType getUpperBound () {
    return myExistential.isExtends() ? myExistential.getBound()
    : myExistential.getManager().getElementFactory().createTypeByFQClassName("java.lang.Object", getResolveScope());
  }

  public PsiWildcardType getWildcard() {
    return myExistential;
  }
}
