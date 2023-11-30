// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiElement;

import java.util.HashMap;
import java.util.Map;

/**
 * Stores the equivalence of variables and methods, to accurately check the equivalence of reference expressions.
 * Do not share or store instances of this class in a field, because it stores psi elements and will leak memory in this case.
 * @author Bas Leijdekkers
 */
public class TrackingEquivalenceChecker extends EquivalenceChecker {

  private final Map<PsiElement, PsiElement> declarationEquivalence = new HashMap<>();

  @Override
  public void markDeclarationsAsEquivalent(PsiElement element1, PsiElement element2) {
    declarationEquivalence.put(element1, element2);
  }

  @Override
  protected boolean equivalentDeclarations(PsiElement element1, PsiElement element2) {
    return super.equivalentDeclarations(element1, element2) ||
           declarationEquivalence.get(element1) == element2 ||
           element1 == declarationEquivalence.get(element2);
  }
}
