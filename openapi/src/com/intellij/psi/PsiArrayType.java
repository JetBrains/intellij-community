/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author max
 */
public class PsiArrayType extends PsiType {
  private PsiType myComponentType;

  public PsiArrayType(PsiType componentType) {
    myComponentType = componentType;
  }

  public String getPresentableText() {
    return myComponentType.getPresentableText() + "[]";
  }

  public String getCanonicalText() {
    return myComponentType.getCanonicalText() + "[]";
  }

  public String getInternalCanonicalText() {
    return myComponentType.getInternalCanonicalText() + "[]";
  }

  public boolean isValid() {
    return myComponentType.isValid();
  }

  public boolean equalsToText(String text) {
    return text.endsWith("[]") && myComponentType.equalsToText(text.substring(0, text.length() - 2));
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return visitor.visitArrayType(this);
  }

  public GlobalSearchScope getResolveScope() {
    return myComponentType.getResolveScope();
  }

  public PsiType[] getSuperTypes() {
    final PsiType[] superTypes = myComponentType.getSuperTypes();
    final PsiType[] result = new PsiType[superTypes.length];
    for (int i = 0; i < superTypes.length; i++) {
      result[i] = superTypes[i].createArrayType();
    }
    return superTypes;
  }

  public PsiType getComponentType() {
    return myComponentType;
  }

  public boolean equals(Object obj) {
    if (!getClass().equals(obj.getClass())) return false;
    return myComponentType.equals(((PsiArrayType)obj).getComponentType());
  }

  public int hashCode() {
    return myComponentType.hashCode() * 3;
  }
}
