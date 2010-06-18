/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;

/**
 * @author max
 */
public class MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {

  public MethodUsagesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(MethodReferencesSearch.SearchParameters p, Processor<PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final SearchRequestCollector collector = p.getOptimizer();

    final SearchScope searchScope = p.getScope();

    final PsiManager psiManager = PsiManager.getInstance(method.getProject());

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    final boolean strictSignatureSearch = p.isStrictSignatureSearch();

    if (method.isConstructor()) {
      collector.searchCustom(new Processor<Processor<PsiReference>>() {
        public boolean process(Processor<PsiReference> consumer) {
          return new ConstructorReferencesSearchHelper(psiManager).
            processConstructorReferences(consumer, method, searchScope, !strictSignatureSearch, strictSignatureSearch);
        }
      });
    }

    boolean needStrictSignatureSearch = method.isValid() && strictSignatureSearch && (aClass instanceof PsiAnonymousClass
                                                                                        || aClass.hasModifierProperty(PsiModifier.FINAL)
                                                                                        || method.hasModifierProperty(PsiModifier.STATIC)
                                                                                        || method.hasModifierProperty(PsiModifier.FINAL)
                                                                                        || method.hasModifierProperty(PsiModifier.PRIVATE));
    if (needStrictSignatureSearch) {
      ReferencesSearch.search(new ReferencesSearch.SearchParameters(method, searchScope, false, collector)).forEach(consumer);
      return;
    }

    final String textToSearch = method.getName();
    final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(textToSearch, false);

    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      accessScope = accessScope.union(method1.getUseScope());
    }

    final SearchScope restrictedByAccess = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
    collector.searchWord(textToSearch, restrictedByAccess, searchContext, true, new MethodTextOccurenceProcessor(aClass, strictSignatureSearch, methods));

    SimpleAccessorReferenceSearcher.addPropertyAccessUsages(method, restrictedByAccess, collector);
  }

}
