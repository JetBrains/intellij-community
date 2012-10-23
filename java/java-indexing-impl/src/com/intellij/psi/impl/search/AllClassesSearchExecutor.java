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
import com.intellij.openapi.util.Computable;
import com.intellij.psi.JavaRecursiveElementWalkingVisitor;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllClassesSearch.SearchParameters queryParameters, @NotNull final Processor<PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();

    if (scope instanceof GlobalSearchScope) {
      return processAllClassesInGlobalScope((GlobalSearchScope)scope, consumer, queryParameters);
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)scope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, consumer)) return false;
    }
    return true;
  }

  private static boolean processAllClassesInGlobalScope(final GlobalSearchScope scope, final Processor<PsiClass> processor, AllClassesSearch.SearchParameters parameters) {
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(parameters.getProject());

    final String[] names = ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      @Override
      public String[] compute() {
        return cache.getAllClassNames();
      }
    });

    final ProgressIndicator indicator = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (indicator != null) {
      indicator.checkCanceled();
    }

    List<String> sorted = new ArrayList<String>(names.length);
    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      if (parameters.nameMatches(name)) {
        sorted.add(name);
      }
      if (indicator != null && i % 512 == 0) {
        indicator.checkCanceled();
      }
    }

    if (indicator != null) {
      indicator.checkCanceled();
    }

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

  private static boolean processScopeRootForAllClasses(PsiElement scopeRoot, final Processor<PsiClass> processor) {
    if (scopeRoot == null) return true;
    final boolean[] stopped = new boolean[]{false};

    scopeRoot.accept(new JavaRecursiveElementWalkingVisitor() {
      @Override
      public void visitElement(PsiElement element) {
        if (!stopped[0]) {
          super.visitElement(element);
        }
      }

      @Override public void visitClass(PsiClass aClass) {
        stopped[0] = !processor.process(aClass);
        super.visitClass(aClass);
      }
    });

    return !stopped[0];
  }
}
