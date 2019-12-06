// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

public class JavaRecordComponentSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof PsiRecordComponent) {
      RecordMethodNavigationInfo info = findNavigationInfo((PsiRecordComponent)element);
      if (info != null) {
        queryParameters.getOptimizer().searchWord(info.myName,
                                                  queryParameters.getEffectiveSearchScope(),
                                                  UsageSearchContext.IN_CODE,
                                                  false,
                                                  info.myLightMethod);
      }
    }
  }

  private static RecordMethodNavigationInfo findNavigationInfo(PsiRecordComponent recordComponent) {
    return ReadAction.compute(() -> {
      String name = recordComponent.getName();
      if (name == null) return null;
      PsiClass containingClass = recordComponent.getContainingClass();
      if (containingClass == null) return null;
      PsiMethod[] methods = containingClass.findMethodsByName(name, false);
      if (methods.length != 1) return null;
      PsiMethod method = methods[0];
      return new RecordMethodNavigationInfo(method, name);
    });
  }

  private static class RecordMethodNavigationInfo {
    @NotNull final PsiMethod myLightMethod;
    @NotNull final String myName;

    private RecordMethodNavigationInfo(@NotNull PsiMethod lightMethod, @NotNull String name) {
      myLightMethod = lightMethod;
      myName = name;
    }
  }
}
