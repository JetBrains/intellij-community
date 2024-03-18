// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchRequestCollector;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class JavaRecordComponentSearcher extends QueryExecutorBase<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ReferencesSearch.SearchParameters queryParameters, @NotNull Processor<? super PsiReference> consumer) {
    PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof PsiRecordComponent recordComponent) {
      SearchScope scope = queryParameters.getEffectiveSearchScope();
      RecordNavigationInfo info = findNavigationInfo(recordComponent);
      if (info != null) {
        SearchRequestCollector optimizer = queryParameters.getOptimizer();
        optimizer.searchWord(info.myName,
                             ReadAction.compute(() -> info.myLightMethod.getUseScope().intersectWith(scope)),
                             true,
                             info.myLightMethod);

        optimizer.searchWord(info.myName,
                             ReadAction.compute(() -> info.myLightField.getUseScope().intersectWith(scope)),
                             true,
                             info.myLightField);

        PsiParameter parameter = info.myLightCompactConstructorParameter;
        if (parameter != null) {
          optimizer.searchWord(info.myName,
                               ReadAction.compute(() -> new LocalSearchScope(parameter.getDeclarationScope())),
                               true,
                               parameter);
        }
      }
    }
  }

  private static RecordNavigationInfo findNavigationInfo(PsiRecordComponent recordComponent) {
    return ReadAction.compute(() -> {
      String name = recordComponent.getName();
      PsiClass containingClass = recordComponent.getContainingClass();
      if (containingClass == null) return null;

      List<PsiMethod> methods = ContainerUtil.filter(containingClass.findMethodsByName(name, false), m -> m.getParameterList().isEmpty());
      if (methods.size() != 1) return null;

      PsiField field = containingClass.findFieldByName(name, false);
      if (field == null) return null;

      PsiMethod compactConstructor = ContainerUtil.find(containingClass.getConstructors(), JavaPsiRecordUtil::isCompactConstructor);
      PsiParameter parameter = compactConstructor != null
                               ? ContainerUtil.find(compactConstructor.getParameterList().getParameters(), p -> name.equals(p.getName()))
                               : null;
      return new RecordNavigationInfo(methods.get(0), field, parameter, name);
    });
  }

  private static final class RecordNavigationInfo {
    @NotNull final PsiMethod myLightMethod;
    @NotNull final PsiField myLightField;
    @Nullable final PsiParameter myLightCompactConstructorParameter;
    @NotNull final String myName;

    private RecordNavigationInfo(@NotNull PsiMethod lightMethod,
                                 @NotNull PsiField lightField,
                                 @Nullable PsiParameter parameter,
                                 @NotNull String name) {
      myLightMethod = lightMethod;
      myLightField = lightField;
      myLightCompactConstructorParameter = parameter;
      myName = name;
    }
  }
}
