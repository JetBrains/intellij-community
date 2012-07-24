package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.MethodReferencesSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class MethodUsagesSearcher extends QueryExecutorBase<PsiReference, MethodReferencesSearch.SearchParameters> {
  protected MethodUsagesSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull MethodReferencesSearch.SearchParameters p, @NotNull final Processor<PsiReference> consumer) {
    final PsiMethod method = p.getMethod();
    final SearchRequestCollector collector = p.getOptimizer();

    final SearchScope searchScope = p.getScope();

    final PsiManager psiManager = method.getManager();

    final PsiClass aClass = method.getContainingClass();
    if (aClass == null) return;

    final boolean strictSignatureSearch = p.isStrictSignatureSearch();

    if (method.isConstructor()) {
      new ConstructorReferencesSearchHelper(psiManager).
        processConstructorReferences(consumer, method, searchScope, !strictSignatureSearch, strictSignatureSearch, collector);
    }

    if (PsiUtil.isAnnotationMethod(method) &&
        PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) &&
        method.getParameterList().getParametersCount() == 0) {
      ReferencesSearch.search(method.getContainingClass(), p.getScope()).forEach(PsiAnnotationMethodReferencesSearcher.createImplicitDefaultAnnotationMethodConsumer(
        consumer));
    }

    boolean needStrictSignatureSearch = strictSignatureSearch && (aClass instanceof PsiAnonymousClass
                                                             || aClass.hasModifierProperty(PsiModifier.FINAL)
                                                             || method.hasModifierProperty(PsiModifier.STATIC)
                                                             || method.hasModifierProperty(PsiModifier.FINAL)
                                                             || method.hasModifierProperty(PsiModifier.PRIVATE));
    if (needStrictSignatureSearch) {
      ReferencesSearch.searchOptimized(method, searchScope, false, collector, consumer);
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
    collector.searchWord(textToSearch, restrictedByAccess, searchContext, true,
                         new MethodTextOccurrenceProcessor(aClass, strictSignatureSearch, methods));

    SimpleAccessorReferenceSearcher.addPropertyAccessUsages(method, restrictedByAccess, collector);

  }

}
