/*
 * Copyright 2000-2011 JetBrains s.r.o.
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

import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
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
    if (!refElement.isValid() || !(refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter)) return;

    final String name = ((PsiVariable)refElement).getName();
    if (name == null) return;

    final SearchScope scope = p.getEffectiveSearchScope();
    if (!(scope instanceof LocalSearchScope)) return;

    PsiElement[] elements = ((LocalSearchScope)scope).getScope();
    if (elements == null || elements.length == 0) return;

    PsiElementProcessor processor = new PsiElementProcessor() {
      @Override
      public boolean execute(@NotNull final PsiElement element) {
        if (element instanceof PsiJavaCodeReferenceElement) {
          final PsiJavaCodeReferenceElement ref = (PsiJavaCodeReferenceElement)element;
          if (!ref.isQualified() && name.equals(ref.getText()) &&
              ref.resolve() == null && ref.advancedResolve(true).getElement() == refElement) {
            consumer.process(ref);
          }
        }
        return true;
      }
    };

    for (PsiElement element : elements) {
      if (element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) {
        PsiTreeUtil.processElements(element, processor);
      }
    }
  }
}
