// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi.impl.search;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.util.Consumer;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import com.intellij.util.indexing.IdFilter;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class AllClassesSearchExecutor implements QueryExecutor<PsiClass, AllClassesSearch.SearchParameters> {
  @Override
  public boolean execute(@NotNull final AllClassesSearch.SearchParameters queryParameters, @NotNull final Processor<? super PsiClass> consumer) {
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
                                                        @NotNull Processor<? super PsiClass> processor) {
    final Set<String> names = new THashSet<>(10000);
    processClassNames(parameters.getProject(), scope, s -> {
      if (parameters.nameMatches(s)) {
        names.add(s);
      }
    });

    List<String> sorted = new ArrayList<>(names);
    Collections.sort(sorted, String.CASE_INSENSITIVE_ORDER);

    return processClassesByNames(parameters.getProject(), scope, sorted, processor);
  }

  public static boolean processClassesByNames(Project project,
                                              final GlobalSearchScope scope,
                                              Collection<String> names,
                                              Processor<? super PsiClass> processor) {
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

  public static Project processClassNames(final Project project, final GlobalSearchScope scope, final Consumer<String> consumer) {
    DumbService.getInstance(project).runReadActionInSmartMode((Computable<Void>)() -> {
      PsiShortNamesCache.getInstance(project).processAllClassNames(s -> {
        ProgressManager.checkCanceled();
        consumer.consume(s);
        return true;
      }, scope, IdFilter.getProjectIdFilter(project, true));
      return null;
    });

    ProgressManager.checkCanceled();
    return project;
  }

  private static boolean processScopeRootForAllClasses(@NotNull final PsiElement scopeRoot, @NotNull final Processor<? super PsiClass> processor) {
    final boolean[] stopped = {false};

    final JavaElementVisitor visitor = scopeRoot instanceof PsiCompiledElement ? new JavaRecursiveElementVisitor() {
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
    ApplicationManager.getApplication().runReadAction(() -> scopeRoot.accept(visitor));

    return !stopped[0];
  }
}
