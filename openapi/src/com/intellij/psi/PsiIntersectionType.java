/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author ven
 */

// Intersection types arise in the process of computing lub.
public class PsiIntersectionType extends PsiType {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.PsiIntersectionType");
  private PsiType[] myConjuncts;

  private PsiIntersectionType(PsiType[] conjuncts) {
    LOG.assertTrue(conjuncts.length > 1);
    myConjuncts = conjuncts;
  }

  public PsiType[] getConjuncts() {
    return myConjuncts;
  }

  public String getPresentableText() {
    return myConjuncts[0].getPresentableText();
  }

  public String getCanonicalText() {
    return myConjuncts[0].getCanonicalText();
  }

  public String getInternalCanonicalText() {
    StringBuffer buffer = new StringBuffer();
    for (int i = 0; i < myConjuncts.length; i++) {
      buffer.append(myConjuncts[i].getInternalCanonicalText());
      if (i < myConjuncts.length - 1) buffer.append(" & ");
    }
    return buffer.toString();
  }

  public boolean isValid() {
    for (int i = 0; i < myConjuncts.length; i++) {
      if (!myConjuncts[i].isValid()) return false;
    }
    return true;
  }

  public boolean equalsToText(String text) {
    return false;
  }

  public <A> A accept(PsiTypeVisitor<A> visitor) {
    return myConjuncts[0].accept(visitor);
  }

  public GlobalSearchScope getResolveScope() {
    return myConjuncts[0].getResolveScope();
  }

  public PsiType[] getSuperTypes() {
    return myConjuncts;
  }

  public static PsiType createIntersection (PsiType[] conjuncts) {
    LOG.assertTrue(conjuncts.length >=1);
    if (conjuncts.length == 1) return conjuncts[0];
    return new PsiIntersectionType(conjuncts);
  }

  public PsiType getRepresentative() {
    return myConjuncts[0];
  }
}
