/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;


/**
 * @author ven
 */
public class PsiEllipsisType extends PsiArrayType {
  public PsiEllipsisType(PsiType componentType) {
    super(componentType);
  }

  public String getPresentableText() {
    return getComponentType().getPresentableText() + "...";
  }

  public String getCanonicalText() {
    return getComponentType().getCanonicalText() + "...";
  }

  public boolean equalsToText(String text) {
    return (text.endsWith("...") && getComponentType().equalsToText(text.substring(0, text.length() - 3))) ||
           super.equalsToText(text);
  }

  public PsiType toArrayType() {
    return getComponentType().createArrayType();
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitEllipsisType(this);
  }

  public boolean equals(Object obj) {
    return obj instanceof PsiEllipsisType && super.equals(obj);
  }

  public int hashCode() {
    return super.hashCode() * 5;
  }
}
