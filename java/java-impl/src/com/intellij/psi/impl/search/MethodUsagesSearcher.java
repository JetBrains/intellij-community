/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
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
  private static final ThreadLocal<Boolean> ourProcessing = new ThreadLocal<Boolean>();

  public boolean execute(final MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    if (ourProcessing.get() != null) {
      return true;
    }

    final PsiSearchRequest.ComplexRequest collector = PsiSearchRequest.composite();
    final PsiMethod method = p.getMethod();

    ApplicationManager.getApplication().runReadAction(new Runnable() {
      public void run() {
        final FindUsagesOptions options = new FindUsagesOptions(p.getScope());
        options.isUsages = true;
        contributeSearchTargets(method, options, collector, consumer, p.isStrictSignatureSearch(), true);
        SearchRequestor.contributeTargets(method, options, collector, consumer);
      }
    });



    return method.getManager().getSearchHelper().processRequest(collector);
  }

  @Override
  public void contributeSearchTargets(@NotNull PsiElement target,
                                      @NotNull final FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      final Processor<PsiReference> processor) {
    if (target instanceof PsiMethod) {
      final boolean strictSignatureSearch = !options.isIncludeOverloadUsages;
      final PsiMethod method = (PsiMethod)target;
      contributeSearchTargets(method, options, collector, processor, strictSignatureSearch, false);
      collector.addRequest(PsiSearchRequest.custom(new Computable<Boolean>() {
        public Boolean compute() {
          ourProcessing.set(true);
          try {
            return MethodReferencesSearch.search(method, options.searchScope, strictSignatureSearch).forEach(processor);
          }
          finally {
            ourProcessing.set(null);
          }
        }
      }));
    }
  }

  private static void contributeSearchTargets(@NotNull final PsiMethod method,
                                      @NotNull FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      final Processor<PsiReference> consumer, final boolean strictSignatureSearch, final boolean fromSearcher) {
    final SearchScope searchScope = options.searchScope;

    final PsiManager psiManager = PsiManager.getInstance(method.getProject());

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    if (method.isConstructor()) {
      collector.addRequest(PsiSearchRequest.custom(new Computable<Boolean>() {
        public Boolean compute() {
          return new ConstructorReferencesSearchHelper(psiManager).
            processConstructorReferences(consumer, method, searchScope, !strictSignatureSearch, strictSignatureSearch);
        }
      }));
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
    collector.addRequest(PsiSearchRequest.elementsWithWord(restrictedByAccess, textToSearch, searchContext, true,
                                                           new MethodTextOccurenceProcessor(consumer, aClass, strictSignatureSearch, methods)));
  }

}
