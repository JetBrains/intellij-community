// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull final MethodReferencesSearch.SearchParameters p, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final boolean[] isConstructor = new boolean[1];
    final PsiManager[] psiManager = new PsiManager[1];
    final String[] methodName = new String[1];
    final boolean[] isValueAnnotation = new boolean[1];
    final boolean[] needStrictSignatureSearch = new boolean[1];
    final boolean strictSignatureSearch = p.isStrictSignatureSearch();

    final PsiClass aClass = DumbService.getInstance(p.getProject()).runReadActionInSmartMode(() -> {
      PsiClass aClass1 = method.getContainingClass();
      if (aClass1 == null) return null;
      isConstructor[0] = method.isConstructor();
      psiManager[0] = aClass1.getManager();
      methodName[0] = method.getName();
      isValueAnnotation[0] = PsiUtil.isAnnotationMethod(method) &&
                             PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(methodName[0]) &&
                             method.getParameterList().isEmpty();
      needStrictSignatureSearch[0] = strictSignatureSearch && (aClass1 instanceof PsiAnonymousClass
                                                               || aClass1.hasModifierProperty(PsiModifier.FINAL)
                                                               || method.hasModifierProperty(PsiModifier.STATIC)
                                                               || method.hasModifierProperty(PsiModifier.FINAL)
                                                               || method.hasModifierProperty(PsiModifier.PRIVATE));
      return aClass1;
    });
    if (aClass == null) return;

    final SearchRequestCollector collector = p.getOptimizer();

    final SearchScope searchScope = DumbService.getInstance(p.getProject()).runReadActionInSmartMode(p::getEffectiveSearchScope);
    if (searchScope == GlobalSearchScope.EMPTY_SCOPE) {
      return;
    }

    if (isConstructor[0]) {
      new ConstructorReferencesSearchHelper(psiManager[0]).
        processConstructorReferences(consumer, method, aClass, searchScope, p.getProject(), false, strictSignatureSearch, collector);
    }

    if (isValueAnnotation[0]) {
      Processor<PsiReference> refProcessor = PsiAnnotationMethodReferencesSearcher.createImplicitDefaultAnnotationMethodConsumer(consumer);
      ReferencesSearch.search(aClass, searchScope).forEach(refProcessor);
    }

    if (needStrictSignatureSearch[0]) {
      ReferencesSearch.searchOptimized(method, searchScope, false, collector, consumer);
      return;
    }

    if (StringUtil.isEmpty(methodName[0])) {
      return;
    }

    DumbService.getInstance(p.getProject()).runReadActionInSmartMode(()-> {
      final PsiMethod[] methods = strictSignatureSearch ? new PsiMethod[]{method} : aClass.findMethodsByName(methodName[0], false);

      short searchContext = UsageSearchContext.IN_CODE | UsageSearchContext.IN_COMMENTS | UsageSearchContext.IN_FOREIGN_LANGUAGES;
      for (PsiMethod m : methods) {
        SearchScope methodUseScope = PsiSearchHelper.getInstance(p.getProject()).getUseScope(m);
        collector.searchWord(methodName[0], searchScope.intersectWith(methodUseScope), searchContext, true, m,
                             getTextOccurrenceProcessor(new PsiMethod[] {m}, aClass, strictSignatureSearch));
      }

      SearchScope accessScope = methods[0].getUseScope();
      for (int i = 1; i < methods.length; i++) {
        PsiMethod method1 = methods[i];
        accessScope = accessScope.union(method1.getUseScope());
      }
      SearchScope restrictedByAccessScope = searchScope.intersectWith(accessScope);
      SimpleAccessorReferenceSearcher.addPropertyAccessUsages(method, restrictedByAccessScope, collector);
      return null;
    });
  }

  protected MethodTextOccurrenceProcessor getTextOccurrenceProcessor(PsiMethod[] methods, PsiClass aClass, boolean strictSignatureSearch) {
    return new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods);
  }
}
