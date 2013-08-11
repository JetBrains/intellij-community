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
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.TimedReference;
import org.jetbrains.annotations.NotNull;

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  private static final Key<TimedReference<SoftReference<Pair<String[], Long>>>> ALL_CLASS_NAMES_CACHE = Key.create("ALL_CLASS_NAMES_CACHE");
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

  @NotNull
  private static String[] getAllClassNames(@NotNull final Project project) {
    return ApplicationManager.getApplication().runReadAction(new Computable<String[]>() {
      @Override
      public String[] compute() {
        final long modCount = PsiManager.getInstance(project).getModificationTracker().getJavaStructureModificationCount();
        TimedReference<SoftReference<Pair<String[], Long>>> ref1 = project.getUserData(ALL_CLASS_NAMES_CACHE);
        SoftReference<Pair<String[], Long>> ref2 = ref1 == null ? null : ref1.get();
        Pair<String[], Long> pair = ref2 == null ? null : ref2.get();
        if (pair != null && pair.second.equals(modCount)) {
          return pair.first;
        }

        String[] names = PsiShortNamesCache.getInstance(project).getAllClassNames();
        ref1 = new TimedReference<SoftReference<Pair<String[], Long>>>(null);
        ref1.set(new SoftReference<Pair<String[], Long>>(Pair.create(names, modCount)));
        project.putUserData(ALL_CLASS_NAMES_CACHE, ref1);
        return names;
      }
    });
  }

  private static boolean processAllClassesInGlobalScope(@NotNull final GlobalSearchScope scope,
                                                        @NotNull AllClassesSearch.SearchParameters parameters,
                                                        @NotNull Processor<PsiClass> processor) {
    String[] names = getAllClassNames(parameters.getProject());
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

    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(parameters.getProject());
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
