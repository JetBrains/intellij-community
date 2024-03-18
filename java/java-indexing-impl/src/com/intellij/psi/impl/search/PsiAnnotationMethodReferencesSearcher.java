// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class PsiAnnotationMethodReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final ReferencesSearch.SearchParameters p, @NotNull final Processor<? super PsiReference> consumer) {
    final PsiElement refElement = p.getElementToSearch();
    boolean isAnnotation = ReadAction.compute(() -> PsiUtil.isAnnotationMethod(refElement));
    if (isAnnotation) {
      final PsiMethod method = (PsiMethod)refElement;
      PsiClass containingClass = ReadAction.compute(() -> {
        boolean isValueMethod =
          PsiAnnotation.DEFAULT_REFERENCED_METHOD_NAME.equals(method.getName()) && method.getParameterList().isEmpty();
        return isValueMethod ? method.getContainingClass() : null;
      });
      if (containingClass != null) {
        SearchScope scope = ReadAction.compute(() -> p.getEffectiveSearchScope());
        final Query<PsiReference> query = ReferencesSearch.search(containingClass, scope, p.isIgnoreAccessScope());
        return query.forEach(createImplicitDefaultAnnotationMethodConsumer(consumer));
      }
    }

    return true;
  }

  @NotNull
  static ReadActionProcessor<PsiReference> createImplicitDefaultAnnotationMethodConsumer(@NotNull Processor<? super PsiReference> consumer) {
    return new ReadActionProcessor<>() {
      @Override
      public boolean processInReadAction(final PsiReference reference) {
        if (reference instanceof PsiJavaCodeReferenceElement javaReference &&
            javaReference.getParent() instanceof PsiAnnotation annotation) {
          PsiNameValuePair[] members = annotation.getParameterList().getAttributes();
          if (members.length == 1 && members[0].getNameIdentifier() == null) {
            PsiReference t = members[0].getReference();
            return t == null || consumer.process(t);
          }
        }
        return true;
      }
    };
  }
}
