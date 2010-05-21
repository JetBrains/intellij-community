/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;

/**
 * @author ven
 */
public class SimpleAccessorReferenceSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(final ReferencesSearch.SearchParameters queryParameters, final Processor<PsiReference> consumer) {
    final PsiElement refElement = queryParameters.getElementToSearch();
    if (!(refElement instanceof PsiMethod)) return true;
    final PsiMethod method = (PsiMethod)refElement;
    final String propertyName = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        if (!method.isValid()) return null;
        return PropertyUtil.getPropertyName(method);
      }
    });
    if (StringUtil.isEmptyOrSpaces(propertyName)) {
      return true;
    }
    SearchScope searchScope = ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      public SearchScope compute() {
        SearchScope searchScope = queryParameters.getEffectiveSearchScope();
        if (searchScope instanceof GlobalSearchScope) {
          searchScope = GlobalSearchScope.getScopeRestrictedByFileTypes((GlobalSearchScope)searchScope,
                                                                        StdFileTypes.JSP,
                                                                        StdFileTypes.JSPX,
                                                                        StdFileTypes.XML,
                                                                        StdFileTypes.XHTML);
        }
        return searchScope;
      }
    });

    final PsiSearchHelper helper = PsiManager.getInstance(refElement.getProject()).getSearchHelper();
    final TextOccurenceProcessor processor = new TextOccurenceProcessor() {
      public boolean execute(PsiElement element, int offsetInElement) {
        final PsiReference[] refs = element.getReferences();
        for (PsiReference ref : refs) {
          if (ReferenceRange.containsOffsetInElement(ref, offsetInElement)) {
            if (ref.isReferenceTo(refElement)) {
              return consumer.process(ref);
            }
          }
        }
        return true;
      }
    };

    return helper.processElementsWithWord(processor, searchScope, propertyName, UsageSearchContext.IN_FOREIGN_LANGUAGES, false);
  }
}
