// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.psi.search.searches.ScopedQueryExecutor;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;


public final class ClassesWithAnnotatedMembersSearcher extends QueryExecutorBase<PsiClass,ClassesWithAnnotatedMembersSearch.Parameters> {
  @Override
  public void processQuery(@NotNull ClassesWithAnnotatedMembersSearch.Parameters queryParameters,
                           final @NotNull Processor<? super PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();
    for (QueryExecutor<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters> executor : ClassesWithAnnotatedMembersSearch.EP_NAME.getExtensionList()) {
      if (executor instanceof ScopedQueryExecutor<PsiClass, ClassesWithAnnotatedMembersSearch.Parameters> scopedQueryExecutor) {
        scope = scope.intersectWith(GlobalSearchScope.notScope(scopedQueryExecutor.getScope(queryParameters)));
      }
    }

    final Set<PsiClass> processed = new HashSet<>();
    AnnotatedElementsSearch.searchPsiMembers(queryParameters.getAnnotationClass(), scope).forEach(member -> {
      PsiClass psiClass = ReadAction.compute(() -> member instanceof PsiClass cls ? cls : member.getContainingClass());

      if (psiClass != null && processed.add(psiClass)) {
        consumer.process(psiClass);
      }

      return true;
    });
  }
}
