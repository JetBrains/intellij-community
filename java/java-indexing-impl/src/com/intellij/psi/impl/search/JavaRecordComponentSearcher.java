// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JavaRecordComponentSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof PsiRecordComponent) {
      PsiRecordComponent recordComponent = (PsiRecordComponent)element;
      RecordNavigationInfo info = findNavigationInfo(recordComponent);
      if (info != null) {
        SearchRequestCollector optimizer = queryParameters.getOptimizer();
        optimizer.searchWord(info.myName,
                             queryParameters.getEffectiveSearchScope(),
                             false,
                             info.myLightMethod);

        optimizer.searchWord(info.myName,
                             new LocalSearchScope(info.myClass),
                             true,
                             info.myLightField);

        PsiParameter parameter = info.myLightCompactConstructorParameter;
        if (parameter != null) {
          optimizer.searchWord(info.myName,
                               new LocalSearchScope(parameter.getDeclarationScope()),
                               true,
                               parameter);
        }
      }
    }
  }

  private static RecordNavigationInfo findNavigationInfo(PsiRecordComponent recordComponent) {
    return ReadAction.compute(() -> {
      String name = recordComponent.getName();
      if (name == null) return null;
      PsiClass containingClass = recordComponent.getContainingClass();
      if (containingClass == null) return null;

      PsiMethod[] methods = containingClass.findMethodsByName(name, false);
      if (methods.length != 1) return null;

      PsiField field = containingClass.findFieldByName(name, false);
      if (field == null) return null;

      PsiMethod compactConstructor = ContainerUtil.find(containingClass.getConstructors(), JavaPsiRecordUtil::isCompactConstructor);
      PsiParameter parameter = compactConstructor != null 
                               ? ContainerUtil.find(compactConstructor.getParameterList().getParameters(), p -> name.equals(p.getName())) 
                               : null;
      return new RecordNavigationInfo(methods[0], field, parameter, name, recordComponent.getContainingClass());
    });
  }

  private static class RecordNavigationInfo {
    @NotNull final PsiMethod myLightMethod;
    @NotNull final PsiField myLightField;
    @Nullable final PsiParameter myLightCompactConstructorParameter;
    @NotNull final String myName;
    @NotNull final PsiClass myClass;

    private RecordNavigationInfo(@NotNull PsiMethod lightMethod,
                                 @NotNull PsiField lightField,
                                 @Nullable PsiParameter parameter, 
                                 @NotNull String name,
                                 @NotNull PsiClass aClass) {
      myLightMethod = lightMethod;
      myLightField = lightField;
      myLightCompactConstructorParameter = parameter;
      myName = name;
      myClass = aClass;
    }
  }
}
