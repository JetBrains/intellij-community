// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * Looks for references to local variable or method parameter in invalid (incomplete) code.
 */
public class VariableInIncompleteCodeSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  public VariableInIncompleteCodeSearcher() {
    super(true);
  }

  @Override
  public void processQuery(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    if (!refElement.isValid() || !(refElement instanceof PsiVariable)) return;

    final String name = ((PsiVariable)refElement).getName();
    if (StringUtil.isEmptyOrSpaces(name)) return;

    SearchScope scope = p.getEffectiveSearchScope();
    if (!(scope instanceof LocalSearchScope)) {
      final PsiFile file = refElement.getContainingFile();
      if (file == null || file instanceof PsiCompiledElement) return;
      //process incomplete references to the 'field' in the same file only
      scope = new LocalSearchScope(new PsiElement[]{file}, null, !PsiSearchHelperImpl.shouldProcessInjectedPsi(p.getScopeDeterminedByUser()));
    }
    else {
      PsiElement[] elements = ((LocalSearchScope)scope).getScope();
      PsiElement[] sourceElements = ContainerUtil.findAllAsArray(elements, e -> !(e instanceof PsiCompiledElement));
      if (sourceElements.length != elements.length) {
        if (sourceElements.length == 0) return;
        scope = new LocalSearchScope(sourceElements);
      }
    }

    PsiElement[] elements = ((LocalSearchScope)scope).getScope();
    if (elements.length == 0) return;

    PsiSearchHelper.getInstance(p.getProject()).processElementsWithWord((element, offsetInElement) -> {
      for (PsiElement child = element.findElementAt(offsetInElement); child != null; child = child.getParent()) {
        if (!child.textMatches(name)) {
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
    }, scope, name, UsageSearchContext.ANY, true);
  }
}
