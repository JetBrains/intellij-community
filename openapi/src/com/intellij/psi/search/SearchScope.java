/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi.search;

import com.intellij.psi.PsiElement;

import java.util.ArrayList;
import java.util.List;


public abstract class SearchScope {
  public String getDisplayName() {
    return "<unknown scope>";
  }

  public SearchScope intersectWith(SearchScope scope){
    return intersection(this, scope);
  }

  private static SearchScope intersection(SearchScope scope1, SearchScope scope2) {
    if (scope1 instanceof LocalSearchScope) {
      if (scope2 instanceof LocalSearchScope) {
        return ((LocalSearchScope)scope1).intersectWith((LocalSearchScope)scope2);
      }
      else {
        return intersection(scope2, scope1);
      }
    }
    else if (scope2 instanceof LocalSearchScope) {
      LocalSearchScope _scope2 = (LocalSearchScope)scope2;
      PsiElement[] elements2 = _scope2.getScope();
      List<PsiElement> result = new ArrayList<PsiElement>();
      for (int i = 0; i < elements2.length; i++) {
        final PsiElement element2 = elements2[i];
        if (PsiSearchScopeUtil.isInScope(scope1, element2)) {
          result.add(element2);
        }
      }
      return new LocalSearchScope((PsiElement[])result.toArray(new PsiElement[result.size()]));
    }
    else {
      return ((GlobalSearchScope)scope1).intersectWith((GlobalSearchScope)scope2);
    }
  }
}
