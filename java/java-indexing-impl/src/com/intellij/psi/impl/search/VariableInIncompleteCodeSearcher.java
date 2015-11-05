/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.*;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

/**
 * Looks for references to local variable or method parameter in invalid (incomplete) code.
 */
public class VariableInIncompleteCodeSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public VariableInIncompleteCodeSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    if (!refElement.isValid() || !(refElement instanceof PsiVariable)) return;

    final String name = ((PsiVariable)refElement).getName();
    if (StringUtil.isEmptyOrSpaces(name)) return;

    SearchScope scope = p.getEffectiveSearchScope();
    if (!(scope instanceof LocalSearchScope)) {
      final PsiFile file = refElement.getContainingFile();
      if (file == null) return;
      //process incomplete references to the 'field' in the same file only
      scope = new LocalSearchScope(new PsiElement[]{file}, null, !PsiSearchHelperImpl.shouldProcessInjectedPsi(p.getScopeDeterminedByUser()));
    }

    PsiElement[] elements = ((LocalSearchScope)scope).getScope();
    if (elements.length == 0) return;

    PsiSearchHelper.SERVICE.getInstance(p.getProject()).processElementsWithWord(new TextOccurenceProcessor() {
      @Override
      public boolean execute(@NotNull PsiElement element, int offsetInElement) {
        for (PsiElement child = element.findElementAt(offsetInElement); child != null; child = child.getParent()) {
          if (!name.equals(child.getText())) {
            break;
          }
          if (child instanceof PsiJavaCodeReferenceElement) {
            final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)child;
            if (!ref.isQualified() &&
                !(ref.getParent() instanceof PsiMethodCallExpression) &&
                ref.resolve() == null && ref.advancedResolve(true).getElement() == refElement) {
              consumer.process(ref);
            }
          }
        }
        return true;
      }
    }, scope, name, UsageSearchContext.ANY, true);
  }
}
