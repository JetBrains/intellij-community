// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.java.indexing.JavaIndexingBundle;
import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.function.Predicate;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ClassInheritorsSearch.SearchParameters parameters, @NotNull Processor<? super PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    assert parameters.isCheckDeep();
    assert parameters.isCheckInheritance();

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ReadAction.compute(baseClass::getName);
      progress.setText(className != null ?
                       JavaIndexingBundle.message("psi.search.inheritors.of.class.progress", className) :
                       JavaIndexingBundle.message("psi.search.inheritors.progress"));
    }

    try {
      processInheritors(parameters, consumer);
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
    }
  }

  private static void processInheritors(@NotNull final ClassInheritorsSearch.SearchParameters parameters,
                                        @NotNull final Processor<? super PsiClass> consumer) {
    @NotNull final PsiClass baseClass = parameters.getClassToProcess();
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return;

    final SearchScope searchScope = parameters.getScope();
    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).allowParallelProcessing().forEach(aClass -> {
        ProgressManager.checkCanceled();
        return isJavaLangObject(aClass) || consumer.process(aClass);
      });
      return;
    }
    if (searchScope instanceof LocalSearchScope && JavaOverridingMethodsSearcher.isJavaOnlyScope(((LocalSearchScope)searchScope).getVirtualFiles())) {
      processLocalScope(project, parameters, (LocalSearchScope)searchScope, baseClass, consumer);
      return;
    }

    if (!parameters.isCheckDeep() && ApplicationManager.getApplication().isDispatchThread()) {
      // optimisation: if under EDT we've been asked for one inheritor, to improve latency do not bother to compute and cache them all
      DirectClassInheritorsSearch.search(baseClass, searchScope).forEach(consumer);
      return;
    }

    Iterable<PsiClass> cached = getOrComputeSubClasses(project, baseClass, searchScope, parameters);

    for (final PsiClass subClass : cached) {
      ProgressManager.checkCanceled();
      if (subClass == null) {
        // PsiAnchor failed to retrieve?
        continue;
      }
      if (ReadAction.compute(() ->
        checkCandidate(subClass, parameters) && !consumer.process(subClass))) {
        return;
      }
    }
  }

  @NotNull
  private static Iterable<@NotNull PsiClass> getOrComputeSubClasses(@NotNull Project project,
                                                                    @NotNull PsiClass baseClass,
                                                                    @NotNull SearchScope searchScopeForNonPhysical,
                                                                    @NotNull ClassInheritorsSearch.SearchParameters parameters) {
    HighlightingCaches caches = HighlightingCaches.getInstance(project);
    ConcurrentMap<PsiClass, Iterable<PsiClass>> map = parameters.isIncludeAnonymous()
                                                      ? caches.ALL_SUB_CLASSES
                                                      : caches.ALL_SUB_CLASSES_NO_ANONYMOUS;
    Iterable<PsiClass> cached = map.get(baseClass);
    if (cached == null) {
      // returns lazy collection of subclasses. Each call to next() leads to calculation of next batch of subclasses.
      Function<@NotNull PsiAnchor, @NotNull PsiClass> converter =
        anchor -> ReadAction.compute(() -> (@NotNull PsiClass)anchor.retrieve());
      Predicate<PsiClass> applicableFilter =
        candidate -> !(candidate instanceof PsiAnonymousClass) && candidate != null && !candidate.hasModifierProperty(PsiModifier.FINAL);
      // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
      boolean isPhysical = ReadAction.compute(baseClass::isPhysical);
      SearchScope scopeToUse = isPhysical ? GlobalSearchScope.allScope(project) : searchScopeForNonPhysical;
      LazyConcurrentCollection.MoreElementsGenerator<PsiAnchor, PsiClass> generator = (candidate, processor) -> DirectClassInheritorsSearch
          .search(new DirectClassInheritorsSearch.SearchParameters(candidate, scopeToUse, parameters.isIncludeAnonymous(), true) {
            @Override
            public boolean shouldSearchInLanguage(@NotNull Language language) {
              return parameters.shouldSearchInLanguage(language);
            }

            @Override
            public ClassInheritorsSearch.SearchParameters getOriginalParameters() {
              return parameters;
            }
          })
          .allowParallelProcessing().forEach(subClass -> {
            ProgressManager.checkCanceled();
            @NotNull PsiAnchor pointer = ReadAction.compute(() -> PsiAnchor.create(subClass));
            // append found result to subClasses as early as possible to allow other waiting threads to continue
            processor.accept(pointer);
            return true;
          });

      PsiAnchor seed = ReadAction.compute(() -> PsiAnchor.create(baseClass));
      // lazy collection: store underlying queue as PsiAnchors, generate new elements by running direct inheritors
      Iterable<PsiClass> computed = new LazyConcurrentCollection<>(seed, converter, applicableFilter, generator);
      // make sure concurrent calls of this method always return the same collection to avoid expensive duplicate work
      cached = isPhysical ? ConcurrencyUtil.cacheOrGet(map, baseClass, computed) : computed;
    }
    return cached;
  }

  private static void processLocalScope(@NotNull final Project project,
                                        @NotNull final ClassInheritorsSearch.SearchParameters parameters,
                                        @NotNull LocalSearchScope searchScope,
                                        @NotNull PsiClass baseClass,
                                        @NotNull Processor<? super PsiClass> consumer) {
    // optimisation: in case of local scope it's considered cheaper to enumerate all scope files and check if there is an inheritor there,
    // instead of traversing the (potentially huge) class hierarchy and filter out almost everything by scope.
    VirtualFile[] virtualFiles = searchScope.getVirtualFiles();

    final boolean[] success = {true};
    if (virtualFiles.length == 0) {
      for (PsiElement element : searchScope.getScope()) {
        processFile(element.getContainingFile(), consumer, parameters, baseClass, success);
      }
    }
    for (VirtualFile virtualFile : virtualFiles) {
      ProgressManager.checkCanceled();
      ApplicationManager.getApplication().runReadAction(() -> {
        PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
        if (psiFile != null) {
          processFile(psiFile, consumer, parameters, baseClass, success);
        }
      });
    }
  }

  private static void processFile(PsiFile psiFile,
                           final Processor<? super PsiClass> consumer,
                           final ClassInheritorsSearch.@NotNull SearchParameters parameters,
                           @NotNull final PsiClass baseClass, final boolean[] success) {
    psiFile.accept(new JavaRecursiveElementVisitor() {
      @Override
      public void visitClass(@NotNull PsiClass candidate) {
        ProgressManager.checkCanceled();
        if (!success[0]) return;
        if (candidate.isInheritor(baseClass, true)
            && checkCandidate(candidate, parameters)
            && !consumer.process(candidate)) {
          success[0] = false;
          return;
        }
        super.visitClass(candidate);
      }
    });
  }

  private static boolean checkCandidate(@NotNull PsiClass candidate, @NotNull ClassInheritorsSearch.SearchParameters parameters) {
    SearchScope searchScope = parameters.getScope();
    ProgressManager.checkCanceled();

    if (!PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
      return false;
    }
    if (candidate instanceof PsiAnonymousClass) {
      return true;
    }

    String name = candidate.getName();
    return name != null && parameters.getNameCondition().value(name);
  }

  static boolean isJavaLangObject(@NotNull final PsiClass baseClass) {
    return ReadAction.compute(() -> baseClass.isValid() && CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName()));
  }

  private static boolean isFinal(@NotNull final PsiClass baseClass) {
    return ReadAction.compute(() -> baseClass.hasModifierProperty(PsiModifier.FINAL));
  }
}
