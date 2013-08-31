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

/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllClassesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();

    if (scope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)scope, queryParameters, consumer);
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)scope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, consumer)) return false;
    }
    return true;
  }

  private static boolean processAllClassesInGlobalScope(@NotNull final GlobalSearchScope scope,
                                                        @NotNull final AllClassesSearch.SearchParameters parameters,
                                                        @NotNull Processor<PsiClass> processor) {
    Project project = parameters.getProject();
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    final ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    
    final Set<String> names = new THashSet<String>(10000);
    cache.processAllClassNames(new Processor<String>() {
      int i = 0;

      @Override
      public boolean process(String s) {
        if (indicator != null && i++ % 512 == 0) {
          indicator.checkCanceled();
        }
        if (parameters.nameMatches(s)) {
          names.add(s);
        }
        return true;
      }
    }, scope, IdFilter.getProjectIdFilter(project, true));

    if (indicator != null) {
      indicator.checkCanceled();
    }

    List<String> sorted = new ArrayList<String>(names);
    Collections.sort(sorted, new Comparator<String>() {
      @Override
      public int compare(final String o1, final String o2) {
        return o1.compareToIgnoreCase(o2);
      }
    });

    for (final String name : sorted) {
      ProgressIndicatorProvider.checkCanceled();
      final PsiClass[] classes = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass[]>() {
        @Override
        public PsiClass[] compute() {
          return cache.getClassesByName(name, scope);
        }
      });
      for (PsiClass psiClass : classes) {
        ProgressIndicatorProvider.checkCanceled();
        if (!processor.process(psiClass)) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean processScopeRootForAllClasses(@NotNull PsiElement scopeRoot, @NotNull final Processor<PsiClass> processor) {
    final boolean[] stopped = {false};

    JavaElementVisitor visitor = scopeRoot instanceof PsiCompiledElement ? new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!stopped[0]) {
          super.visitElement(element);
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.process(aClass);
        super.visitClass(aClass);
      }
    } : new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!stopped[0]) {
          super.visitElement(element);
        }
      }

      @Override
      public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.process(aClass);
        super.visitClass(aClass);
      }
    };
    scopeRoot.accept(visitor);

    return !stopped[0];
  }
}
