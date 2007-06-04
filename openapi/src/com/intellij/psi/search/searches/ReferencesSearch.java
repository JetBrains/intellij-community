/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.search.searches;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import gnu.trove.TObjectHashingStrategy;

/**
 * @author max
 */
public class ReferencesSearch extends ExtensibleQueryFactory<PsiReference, ReferencesSearch.SearchParameters> {
  public static ReferencesSearch INSTANCE = new ReferencesSearch();
  private static final TObjectHashingStrategy<PsiReference> HASHING_STRATEGY = new TObjectHashingStrategy<PsiReference>() {
    public int computeHashCode(final PsiReference object) {
      if (object == null) return 0;
      final PsiElement element = object.getElement();
      final PsiFile file = element.getContainingFile();
      return file.hashCode() + 31 * (element.getTextOffset() + object.getRangeInElement().getStartOffset());
    }

    public boolean equals(final PsiReference o1, final PsiReference o2) {
      if (o1 == o2) return true;
      if (o1 == null || o2 == null) return false;
      final PsiElement e1 = o1.getElement();
      final PsiElement e2 = o2.getElement();
      if (!e1.getManager().areElementsEquivalent(e1.getContainingFile(), e2.getContainingFile())) return false;
      return e1.getTextOffset() + o1.getRangeInElement().getStartOffset() == e2.getTextOffset() + o2.getRangeInElement().getStartOffset();
    }
  };

  private ReferencesSearch() {
  }

  public static class SearchParameters {
    private final PsiElement myElementToSearch;
    private final SearchScope myScope;
    private final boolean myIgnoreAccessScope;

    public SearchParameters(final PsiElement elementToSearch, final SearchScope scope, final boolean ignoreAccessScope) {
      myElementToSearch = elementToSearch;
      myScope = scope;
      myIgnoreAccessScope = ignoreAccessScope;
    }

    public PsiElement getElementToSearch() {
      return myElementToSearch;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isIgnoreAccessScope() {
      return myIgnoreAccessScope;
    }

    public SearchScope getEffectiveSearchScope () {
      if (!myIgnoreAccessScope) {
        SearchScope accessScope = myElementToSearch.getUseScope();
        return myScope.intersectWith(accessScope);
      }
      else {
        return myScope;
      }
    }
  }

  public static Query<PsiReference> search(PsiElement element) {
    return search(element, GlobalSearchScope.projectScope(element.getProject()), false);
  }

  public static Query<PsiReference> search(PsiElement element, SearchScope searchScope) {
    return search(element, searchScope, false);
  }

  public static Query<PsiReference> search(PsiElement element, SearchScope searchScope, boolean ignoreAccessScope) {
    return INSTANCE.createUniqueResultsQuery(new SearchParameters(element, searchScope, ignoreAccessScope), HASHING_STRATEGY);
  }
}
