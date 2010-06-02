/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class MethodUsagesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
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
        contributeSearchTargets(method, options, collector, consumer, p.isStrictSignatureSearch(), false);
      }
    });

    return method.getManager().getSearchHelper().processRequest(collector);
  }

  public static void contributeSearchTargets(@NotNull final PsiMethod method,
                                      @NotNull FindUsagesOptions options,
                                      @NotNull PsiSearchRequest.ComplexRequest collector,
                                      final Processor<PsiReference> consumer, final boolean strictSignatureSearch, boolean callOtherSearchers) {
    final SearchScope searchScope = options.searchScope;

    if (callOtherSearchers) {
      collector.addRequest(PsiSearchRequest.custom(new Runnable() {
        public void run() {
          ourProcessing.set(true);
          try {
            MethodReferencesSearch.search(method, searchScope, strictSignatureSearch).forEach(consumer);
          }
          finally {
            ourProcessing.set(null);
          }
        }
      }));

    }

    final PsiManager psiManager = PsiManager.getInstance(method.getProject());

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    if (method.isConstructor()) {
      collector.addRequest(PsiSearchRequest.custom(new Runnable() {
        public void run() {
          new ConstructorReferencesSearchHelper(psiManager).
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
      SearchRequestor.contributeTargets(method, options, collector, consumer);
      return;
    }

    final String textToSearch = method.getName();
    final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(textToSearch, false);

    SearchScope accessScope = methods[0].getUseScope();
    for (int i = 1; i < methods.length; i++) {
      PsiMethod method1 = methods[i];
      accessScope = accessScope.union(method1.getUseScope());
    }

    final TextOccurenceProcessor processor1 = new MethodTextOccurenceProcessor(consumer, aClass, strictSignatureSearch, methods);

    final SearchScope restrictedByAccess = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
    collector.addRequest(PsiSearchRequest.elementsWithWord(restrictedByAccess, textToSearch, searchContext, true, processor1));

    final String propertyName = PropertyUtil.getPropertyName(method);
    if (StringUtil.isNotEmpty(propertyName)) {
      SearchScope additional = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(psiManager.getProject()),
                                                                               StdFileTypes.JSP, StdFileTypes.JSPX,
                                                                               StdFileTypes.XML, StdFileTypes.XHTML);

      for (CustomPropertyScopeProvider provider : Extensions.getExtensions(CustomPropertyScopeProvider.EP_NAME)) {
        additional = additional.union(provider.getScope(psiManager.getProject()));
      }
      assert propertyName != null;
      final SearchScope propScope = restrictedByAccess.intersectWith(additional);
      collector.addRequest(PsiSearchRequest.elementsWithWord(propScope, propertyName, UsageSearchContext.IN_FOREIGN_LANGUAGES, true, processor1));
    }
  }

}
