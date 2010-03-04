/*
 * Copyright (c) 2005 Your Corporation. All Rights Reserved.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class MethodUsagesSearcher implements QueryExecutor<PsiReference, MethodReferencesSearch.SearchParameters> {
  public boolean execute(final MethodReferencesSearch.SearchParameters p, final Processor<PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final SearchScope searchScope = p.getScope();
    final PsiManager psiManager = PsiManager.getInstance(method.getProject());
    final boolean isStrictSignatureSearch = p.isStrictSignatureSearch();

    final PsiClass aClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
      public PsiClass compute() {
        return method.getContainingClass();
      }
    });
    if (aClass == null) return true;

    if (method.isConstructor()) {
      final ConstructorReferencesSearchHelper helper = new ConstructorReferencesSearchHelper(psiManager);
      if (!helper.processConstructorReferences(consumer, method, searchScope, !isStrictSignatureSearch, isStrictSignatureSearch)) {
        return false;
      }
    }

    boolean needStrictSignatureSearch = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return method.isValid() && isStrictSignatureSearch && (aClass instanceof PsiAnonymousClass
                                        || aClass.hasModifierProperty(PsiModifier.FINAL)
                                        || method.hasModifierProperty(PsiModifier.STATIC)
                                        || method.hasModifierProperty(PsiModifier.FINAL)
                                        || method.hasModifierProperty(PsiModifier.PRIVATE));
      }
    }).booleanValue();
    if (needStrictSignatureSearch) {
      return ReferencesSearch.search(method, searchScope, false).forEach(new ReadActionProcessor<PsiReference>() {
        public boolean processInReadAction(final PsiReference psiReference) {
          return consumer.process(psiReference);
        }
      });
    }

    final String textToSearch = method.getName();
    final PsiMethod[] methods = isStrictSignatureSearch ? new PsiMethod[]{method} : getOverloads(method);

    SearchScope accessScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        if (!method.isValid()) return searchScope;
        SearchScope accessScope = methods[0].getUseScope();
        for (int i = 1; i < methods.length; i++) {
          PsiMethod method1 = methods[i];
          accessScope = accessScope.union(method1.getUseScope());
        }
        return accessScope;
      }
    });

    final TextOccurenceProcessor processor1 = new MethodTextOccurenceProcessor(consumer, aClass, isStrictSignatureSearch, methods);

    final SearchScope restrictedByAccess = searchScope.intersectWith(accessScope);

    short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
    PsiSearchHelper helper = psiManager.getSearchHelper();
    if (!helper.processElementsWithWord(processor1, restrictedByAccess, textToSearch, searchContext, true)) return false;

    final String propertyName = ApplicationManager.getApplication().runReadAction(new Computable<String>(){
      public String compute() {
        if (!method.isValid()) return null;
        return PropertyUtil.getPropertyName(method);
      }
    });
    if (StringUtil.isEmpty(propertyName)) {
      return true;
    }
    final SearchScope scope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        SearchScope additional = GlobalSearchScope.getScopeRestrictedByFileTypes(GlobalSearchScope.allScope(psiManager.getProject()),
                                         StdFileTypes.JSP, StdFileTypes.JSPX,
                                         StdFileTypes.XML, StdFileTypes.XHTML);

        for (CustomPropertyScopeProvider provider : Extensions.getExtensions(CustomPropertyScopeProvider.EP_NAME)) {
          SearchScope s = provider.getScope(psiManager.getProject());
          additional = additional.union(s);
        }

        return restrictedByAccess.intersectWith(additional);
      }
    });
    return helper.processElementsWithWord(processor1, scope, propertyName, UsageSearchContext.IN_FOREIGN_LANGUAGES, true);
  }

  @NotNull
  private static PsiMethod[] getOverloads(final PsiMethod method) {
    return ApplicationManager.getApplication().runReadAction(new Computable<PsiMethod[]>() {
      public PsiMethod[] compute() {
        if (!method.isValid()) return PsiMethod.EMPTY_ARRAY;
        PsiClass aClass = method.getContainingClass();
        if (aClass == null) return new PsiMethod[]{method};
        return aClass.findMethodsByName(method.getName(), false);
      }
    });
  }

}
