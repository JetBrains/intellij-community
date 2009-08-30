package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Used in Generify refactoring
 */
public class Bottom extends PsiType {
  public static final Bottom BOTTOM = new Bottom();

  private Bottom() {
    super(PsiAnnotation.EMPTY_ARRAY);
  }

  public String getPresentableText() {
    return "_";
  }

  public String getCanonicalText() {
    return "_";
  }

  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  public boolean isValid() {
    return true;
  }

  public boolean equalsToText(String text) {
    return text.equals("_");
  }

  public boolean equals(Object o) {
    if (o instanceof Bottom) {
      return true;
    }

    return false;
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    if (visitor instanceof PsiTypeVisitorEx) {
      return ((PsiTypeVisitorEx<A>)visitor).visitBottom(this);
    }
    return visitor.visitType(this);
  }

  @NotNull
  public PsiType[] getSuperTypes() {
    throw new UnsupportedOperationException();
  }

  public GlobalSearchScope getResolveScope() {
    return null;
  }
}
