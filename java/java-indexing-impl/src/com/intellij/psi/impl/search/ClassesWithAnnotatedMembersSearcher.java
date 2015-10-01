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

import com.intellij.openapi.application.*;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMember;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AnnotatedElementsSearch;
import com.intellij.psi.search.searches.ClassesWithAnnotatedMembersSearch;
import com.intellij.psi.search.searches.ScopedQueryExecutor;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/**
 * @author yole
 */
public class ClassesWithAnnotatedMembersSearcher extends QueryExecutorBase<PsiClass,ClassesWithAnnotatedMembersSearch.Parameters> {
  @Override
  public void processQuery(@NotNull ClassesWithAnnotatedMembersSearch.Parameters queryParameters,
                           @NotNull final Processor<PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();
    for (QueryExecutor executor : Extensions.getExtensions(ClassesWithAnnotatedMembersSearch.EP_NAME)) {
      if (executor instanceof ScopedQueryExecutor) {
        scope = scope.intersectWith(GlobalSearchScope.notScope(((ScopedQueryExecutor) executor).getScope(queryParameters)));
      }
    }

    final Set<PsiClass> processed = new HashSet<PsiClass>();
    AnnotatedElementsSearch.searchPsiMembers(queryParameters.getAnnotationClass(), scope).forEach(new Processor<PsiMember>() {
      @Override
      public boolean process(PsiMember member) {
        PsiClass psiClass;
        AccessToken token = ReadAction.start();
        try {
          psiClass = member instanceof PsiClass ? (PsiClass)member : member.getContainingClass();
        }
        finally {
          token.finish();
        }

        if (psiClass != null && processed.add(psiClass)) {
          consumer.process(psiClass);
        }

        return true;
      }
    });
  }
}
