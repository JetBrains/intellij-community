/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class MethodUsagesSearcher extends SearchRequestor implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {

  public boolean execute(final MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    if (p instanceof MySearchParameters) {
      return true;
    }

    final PsiMethod method = p.getMethod();
    final SearchRequestCollector collector = new SearchRequestCollector();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final FindUsagesOptions options = new FindUsagesOptions(p.getScope());
        options.isUsages = true;
        contributeSearchTargets(method, options, collector, p.isStrictSignatureSearch());
        SearchRequestor.collectRequests(method, options, collector);
      }
    });

    return method.getManager().getSearchHelper().processRequests(collector, consumer);
  }

  @Override
  public void contributeRequests(@NotNull PsiElement target,
                                      @NotNull final FindUsagesOptions options,
                                      @NotNull SearchRequestCollector collector) {
    if (target instanceof PsiMethod) {
      final boolean strictSignatureSearch = !options.isIncludeOverloadUsages;
      final PsiMethod method = (PsiMethod)target;
      contributeSearchTargets(method, options, collector, strictSignatureSearch);
      collector.searchCustom(new Processor<Processor<PsiReference>>() {
        public boolean process(Processor<PsiReference> processor) {
          return MethodReferencesSearch.search(new MySearchParameters(method, options, strictSignatureSearch)).forEach(processor);
        }
      });
    }
  }

  private static void contributeSearchTargets(@NotNull final PsiMethod method,
                                              @NotNull FindUsagesOptions options,
                                              @NotNull SearchRequestCollector collector,
                                              final boolean strictSignatureSearch) {
    final SearchScope searchScope = options.searchScope;

    final PsiManager psiManager = PsiManager.getInstance(method.getProject());

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

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
      // CacheBasedRefSearcher deals with that
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
  }

  private static class MySearchParameters extends MethodReferencesSearch.SearchParameters {
    public MySearchParameters(PsiMethod method, FindUsagesOptions options, boolean strictSignatureSearch) {
      super(method, options.searchScope, strictSignatureSearch);
    }
  }
}
