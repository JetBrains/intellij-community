/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;

import java.util.*;

public class LocalSearchScope extends SearchScope {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.LocalSearchScope");

  private final PsiElement[] myScope;

  public static final LocalSearchScope EMPTY = new LocalSearchScope(PsiElement.EMPTY_ARRAY);
  private String myDisplayName;

  public LocalSearchScope(PsiElement scope) {
    this(scope, null);
  }

  public LocalSearchScope(PsiElement scope, String displayName) {
    this(new PsiElement[]{scope});
    myDisplayName = displayName;
  }

  public LocalSearchScope(PsiElement[] scope) {
    this(scope, null);
  }

  public LocalSearchScope(PsiElement[] scope, String displayName) {
    myDisplayName = displayName;
    Set<PsiElement> localScope = new HashSet<PsiElement>(scope.length);

    for (int i = 0; i < scope.length; i++) {
      final PsiElement element = scope[i];
      LOG.assertTrue(element.getContainingFile() != null);
      if (element instanceof PsiFile) {
        localScope.addAll(Arrays.asList(((PsiFile)element).getPsiRoots()));
      }
      else {
        localScope.add(element);
      }
    }
    myScope = localScope.toArray(new PsiElement[localScope.size()]);
  }

  public String getDisplayName() {
    return myDisplayName == null ? super.getDisplayName() : myDisplayName;
  }

  public PsiElement[] getScope() {
    return myScope;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LocalSearchScope)) return false;

    final LocalSearchScope localSearchScope = (LocalSearchScope)o;

    if (localSearchScope.myScope.length != myScope.length) return false;
    for (int i = 0; i < myScope.length; i++) {
      final PsiElement scopeElement = myScope[i];
      final PsiElement[] thatScope = localSearchScope.myScope;
      for (int j = 0; j < thatScope.length; j++) {
        final PsiElement thatScopeElement = thatScope[j];
        if (!Comparing.equal(scopeElement, thatScopeElement)) return false;
      }
    }


    return true;
  }

  public int hashCode() {
    int result = 0;
    for (int i = 0; i < myScope.length; i++) {
      final PsiElement element = myScope[i];
      result += element.hashCode();
    }
    return result;
  }

  public LocalSearchScope intersectWith(LocalSearchScope scope){
    return intersection(this, scope);
  }

  private static LocalSearchScope intersection(LocalSearchScope scope1, LocalSearchScope scope2) {
    List<PsiElement> result = new ArrayList<PsiElement>();
    final PsiElement[] elements1 = scope1.myScope;
    final PsiElement[] elements2 = scope2.myScope;
    for (int i = 0; i < elements1.length; i++) {
      final PsiElement element1 = elements1[i];
      for (int j = 0; j < elements2.length; j++) {
        final PsiElement element2 = elements2[j];
        final PsiElement element = intersectScopeElements(element1, element2);
        if (element != null){
          result.add(element);
        }
      }
    }
    return new LocalSearchScope((PsiElement [])result.toArray(new PsiElement [result.size()]));
  }

  private static PsiElement intersectScopeElements(PsiElement element1, PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element2;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element1;
    return null;
  }

  public String toString() {
    StringBuffer result = new StringBuffer();
    for (int i = 0; i < myScope.length; i++) {
      final PsiElement element = myScope[i];
      if (i > 0) {
        result.append(",");
      }
      result.append(element.toString());
    }
    return "LocalSearchScope:" + result;
  }

  public SearchScope union(LocalSearchScope _scope2) {
    PsiElement[] elements1 = getScope();
    PsiElement[] elements2 = _scope2.getScope();
    boolean[] united = new boolean[elements2.length];
    List<PsiElement> result = new ArrayList<PsiElement>();
    loop1:
    for (int i = 0; i < elements1.length; i++) {
      final PsiElement element1 = elements1[i];
      for (int j = 0; j < elements2.length; j++) {
        final PsiElement element2 = elements2[j];
        final PsiElement unionElement = scopeElementsUnion(element1, element2);
        if (unionElement != null) {
          result.add(unionElement);
          united[j] = true;
          break loop1;
        }
      }
      result.add(element1);
    }
    for (int i = 0; i < united.length; i++) {
      final boolean b = united[i];
      if (!b) {
        result.add(elements2[i]);
      }
    }
    return new LocalSearchScope((PsiElement[])result.toArray(new PsiElement[result.size()]));
  }

  private PsiElement scopeElementsUnion(PsiElement element1, PsiElement element2) {
    if (PsiTreeUtil.isAncestor(element1, element2, false)) return element1;
    if (PsiTreeUtil.isAncestor(element2, element1, false)) return element2;
    PsiElement commonParent = PsiTreeUtil.findCommonParent(element1, element2);
    if (commonParent == null) return null;
    return commonParent;
  }
}
