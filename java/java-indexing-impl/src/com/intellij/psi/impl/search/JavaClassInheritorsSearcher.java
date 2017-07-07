/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.Predicate;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentMap;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ClassInheritorsSearch.SearchParameters parameters, @NotNull Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    assert parameters.isCheckDeep();
    assert parameters.isCheckInheritance();

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ReadAction.compute(baseClass::getName);
      progress.setText(className != null ?
                       PsiBundle.message("psi.search.inheritors.of.class.progress", className) :
                       PsiBundle.message("psi.search.inheritors.progress"));
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
                                        @NotNull final Processor<PsiClass> consumer) {
    @NotNull final PsiClass baseClass = parameters.getClassToProcess();
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return;

    final SearchScope searchScope = parameters.getScope();
    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(aClass -> {
        ProgressManager.checkCanceled();
        return isJavaLangObject(aClass) || consumer.process(aClass);
      });
      return;
    }
    if (searchScope instanceof LocalSearchScope && JavaOverridingMethodsSearcher.isJavaOnlyScope(((LocalSearchScope)searchScope).getVirtualFiles())) {
      processLocalScope(project, parameters, (LocalSearchScope)searchScope, baseClass, consumer);
      return;
    }

    Iterable<PsiClass> cached = getOrComputeSubClasses(project, baseClass, searchScope);

    for (final PsiClass subClass : cached) {
      ProgressManager.checkCanceled();
      if (subClass instanceof PsiAnonymousClass && !parameters.isIncludeAnonymous()) {
        continue;
      }
      if (ReadAction.compute(() ->
        checkCandidate(subClass, parameters) && !consumer.process(subClass))) {
        return;
      }
    }
  }

  @NotNull
  private static Iterable<PsiClass> getOrComputeSubClasses(@NotNull Project project, @NotNull PsiClass baseClass, @NotNull SearchScope searchScopeForNonPhysical) {
    ConcurrentMap<PsiClass, Iterable<PsiClass>> map = HighlightingCaches.getInstance(project).ALL_SUB_CLASSES;
    Iterable<PsiClass> cached = map.get(baseClass);
    if (cached == null) {
      // returns lazy collection of subclasses. Each call to next() leads to calculation of next batch of subclasses.
      Function<PsiAnchor, PsiClass> converter =
        anchor -> ReadAction.compute(() -> (PsiClass)anchor.retrieve());
      Predicate<PsiClass> applicableFilter =
        candidate -> !(candidate instanceof PsiAnonymousClass) && candidate != null && !candidate.hasModifierProperty(PsiModifier.FINAL);
      // for non-physical elements ignore the cache completely because non-physical elements created so often/unpredictably so I can't figure out when to clear caches in this case
      boolean isPhysical = ReadAction.compute(baseClass::isPhysical);
      SearchScope scopeToUse = isPhysical ? GlobalSearchScope.allScope(project) : searchScopeForNonPhysical;
      LazyConcurrentCollection.MoreElementsGenerator<PsiAnchor, PsiClass> generator = (candidate, processor) ->
        DirectClassInheritorsSearch.search(candidate, scopeToUse).forEach(subClass -> {
          ProgressManager.checkCanceled();
          PsiAnchor pointer = ReadAction.compute(() -> PsiAnchor.create(subClass));
          // append found result to subClasses as early as possible to allow other waiting threads to continue
          processor.consume(pointer);
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
                                        @NotNull Processor<PsiClass> consumer) {
    // optimisation: in case of local scope it's considered cheaper to enumerate all scope files and check if there is an inheritor there,
    // instead of traversing the (potentially huge) class hierarchy and filter out almost everything by scope.
    VirtualFile[] virtualFiles = searchScope.getVirtualFiles();

    final boolean[] success = {true};
    for (VirtualFile virtualFile : virtualFiles) {
      ProgressManager.checkCanceled();
      ApplicationManager.getApplication().runReadAction(new Runnable() {
        @Override
        public void run() {
          PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
          if (psiFile != null) {
            psiFile.accept(new JavaRecursiveElementVisitor() {
              @Override
              public void visitClass(PsiClass candidate) {
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
        }
      });
    }
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
