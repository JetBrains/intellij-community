// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllClassesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiClass> consumer) {
    SearchScope scope = queryParameters.getScope();

    if (scope == GlobalSearchScope.EMPTY_SCOPE) {
      return true;
    }

    if (scope instanceof GlobalSearchScope) {
      PsiManager manager = PsiManager.getInstance(queryParameters.getProject());
      manager.startBatchFilesProcessingMode();
      try {
        return processAllClassesInGlobalScope((GlobalSearchScope)scope, queryParameters, consumer);
      }
      finally {
        manager.finishBatchFilesProcessingMode();
      }
    }

    PsiElement[] scopeRoots = ((LocalSearchScope)scope).getScope();
    for (final PsiElement scopeRoot : scopeRoots) {
      if (!processScopeRootForAllClasses(scopeRoot, consumer)) return false;
    }
    return true;
  }

  private static boolean processAllClassesInGlobalScope(@NotNull final GlobalSearchScope scope,
                                                        @NotNull final AllClassesSearch.SearchParameters parameters,
                                                        @NotNull Processor<? super PsiClass> processor) {
    final Set<String> names = new THashSet<>(10000);
    processClassNames(parameters.getProject(), scope, s -> {
      if (parameters.nameMatches(s)) {
        names.add(s);
      }
      return true;
    });

    List<String> sorted = new ArrayList<>(names);
    sorted.sort(String.CASE_INSENSITIVE_ORDER);

    return processClassesByNames(parameters.getProject(), scope, sorted, processor);
  }

  public static boolean processClassesByNames(@NotNull Project project,
                                              @NotNull GlobalSearchScope scope,
                                              @NotNull Collection<String> names,
                                              @NotNull Processor<? super PsiClass> processor) {
    final PsiShortNamesCache cache = PsiShortNamesCache.getInstance(project);
    for (final String name : names) {
      ProgressIndicatorProvider.checkCanceled();
      for (PsiClass psiClass : DumbService.getInstance(project).runReadActionInSmartMode(() -> cache.getClassesByName(name, scope))) {
        ProgressIndicatorProvider.checkCanceled();
        if (!processor.process(psiClass)) {
          return false;
        }
      }
    }
    return true;
  }

  public static boolean processClassNames(@NotNull Project project, @NotNull GlobalSearchScope scope, @NotNull Processor<? super String> processor) {
    boolean success = DumbService.getInstance(project).runReadActionInSmartMode(() ->
      PsiShortNamesCache.getInstance(project).processAllClassNames(s -> {
        ProgressManager.checkCanceled();
        return processor.process(s);
      }, scope, IdFilter.getProjectIdFilter(project, true)));

    ProgressManager.checkCanceled();
    return success;
  }

  private static boolean processScopeRootForAllClasses(@NotNull final PsiElement scopeRoot, @NotNull final Processor<? super PsiClass> processor) {
    final boolean[] stopped = {false};

    final JavaElementVisitor visitor = scopeRoot instanceof PsiCompiledElement ? new JavaRecursiveElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
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
      public void visitElement(@NotNull PsiElement element) {
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
    ApplicationManager.getApplication().runReadAction(() -> scopeRoot.accept(visitor));

    return !stopped[0];
  }
}
