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
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
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
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  @Override
  public void processQuery(@NotNull ClassInheritorsSearch.SearchParameters parameters, @NotNull Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    assert parameters.isCheckDeep();
    assert parameters.isCheckInheritance();

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ApplicationManager.getApplication().runReadAction((Computable<String>)baseClass::getName);
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

  private static boolean processInheritors(@NotNull final ClassInheritorsSearch.SearchParameters parameters,
                                           @NotNull final Processor<PsiClass> consumer) {
    @NotNull final PsiClass baseClass = parameters.getClassToProcess();
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return true;

    final SearchScope searchScope = parameters.getScope();
    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      return AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(aClass -> {
        ProgressManager.checkCanceled();
        return isJavaLangObject(aClass) || consumer.process(aClass);
      });
    }

    Collection<PsiClass> cached = getOrComputeSubClasses(project, parameters);

    Processor<PsiClass> readActionedConsumer = ReadActionProcessor.wrapInReadAction(consumer);
    for (final PsiClass subClass : cached) {
      ProgressManager.checkCanceled();
      if (!readActionedConsumer.process(subClass)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  private static Collection<PsiClass> getOrComputeSubClasses(@NotNull Project project,
                                                             @NotNull ClassInheritorsSearch.SearchParameters parameters) {
    List<PsiClass> computed = null;
    PsiClass baseClass = parameters.getClassToProcess();
    while (true) {
      Collection<PsiClass> cached = null;
      List<Pair<ClassInheritorsSearch.SearchParameters, Collection<PsiClass>>> cachedPairs = HighlightingCaches.getInstance(project).ALL_SUB_CLASSES.get(baseClass);
      if (cachedPairs != null) {
        for (Pair<ClassInheritorsSearch.SearchParameters, Collection<PsiClass>> pair : cachedPairs) {
          ClassInheritorsSearch.SearchParameters cachedParams = pair.getFirst();
          if (cachedParams.equals(parameters)) {
            cached = pair.getSecond();
            break;
          }
        }
      }
      if (cached != null) {
        return cached;
      }
      if (computed == null) {
        computed = new ArrayList<>();
        boolean success = getAllSubClasses(project, parameters, new CommonProcessors.CollectProcessor<>(computed));
        assert success;
      }

      if (cachedPairs != null) {
        cachedPairs.add(Pair.create(parameters, computed));
        break;
      }
      List<Pair<ClassInheritorsSearch.SearchParameters, Collection<PsiClass>>> newCachedPairs =
        ContainerUtil.createConcurrentList(Collections.singletonList(Pair.create(parameters, computed)));
      if (HighlightingCaches.getInstance(project).ALL_SUB_CLASSES.putIfAbsent(baseClass, newCachedPairs) == null) {
        break;
      }
    }
    return computed;
  }

  private static boolean getAllSubClasses(@NotNull Project project,
                                          @NotNull ClassInheritorsSearch.SearchParameters parameters,
                                          @NotNull Processor<PsiClass> consumer) {
    SearchScope searchScope = parameters.getScope();
    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<PsiAnchor> stack = new Stack<>();
    final Set<PsiAnchor> processed = ContainerUtil.newTroveSet();

    boolean checkDeep = searchScope instanceof LocalSearchScope;
    final Processor<PsiClass> processor = new ReadActionProcessor<PsiClass>() {
      @Override
      public boolean processInReadAction(PsiClass candidate) {
        ProgressManager.checkCanceled();

        if (!candidate.isInheritor(currentBase.get(), checkDeep)) {
          return true;
        }

        if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
          if (candidate instanceof PsiAnonymousClass) {
            return consumer.process(candidate);
          }

          final String name = candidate.getName();
          if (name != null && parameters.getNameCondition().value(name) && !consumer.process(candidate)) {
            return false;
          }
        }

        if (!(candidate instanceof PsiAnonymousClass) && !candidate.hasModifierProperty(PsiModifier.FINAL)) {
          stack.push(PsiAnchor.create(candidate));
        }
        return true;
      }
    };

    @NotNull PsiClass baseClass = parameters.getClassToProcess();
    if (searchScope instanceof LocalSearchScope) {
      // optimisation: it's considered cheaper to enumerate all scope files and check if there is an inheritor there,
      // instead of traversing the (potentially huge) class hierarchy and filter out almost everything by scope.
      VirtualFile[] virtualFiles = ((LocalSearchScope)searchScope).getVirtualFiles();
      final boolean[] success = {true};
      currentBase.set(baseClass);
      for (VirtualFile virtualFile : virtualFiles) {
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
            if (psiFile != null) {
              psiFile.accept(new JavaRecursiveElementVisitor() {
                @Override
                public void visitClass(PsiClass aClass) {
                  if (!success[0]) return;
                  if (!processor.process(aClass)) {
                    success[0] = false;
                    return;
                  }
                  super.visitClass(aClass);
                }

                @Override
                public void visitCodeBlock(PsiCodeBlock block) {
                  if (!parameters.isIncludeAnonymous()) return;
                  super.visitCodeBlock(block);
                }
              });
            }
          }
        });
      }
      return success[0];
    }

    ApplicationManager.getApplication().runReadAction(() -> {
      stack.push(PsiAnchor.create(baseClass));
    });
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);

    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();

      final PsiAnchor anchor = stack.pop();
      if (!processed.add(anchor)) continue;

      PsiClass psiClass = ApplicationManager.getApplication().runReadAction((Computable<PsiClass>)() -> (PsiClass)anchor.retrieve());
      if (psiClass == null) continue;

      currentBase.set(psiClass);
      if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) return false;
    }
    return true;
  }

  static boolean isJavaLangObject(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(
      (Computable<Boolean>)() -> baseClass.isValid() && CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName()));
  }

  private static boolean isFinal(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction((Computable<Boolean>)() -> baseClass.hasModifierProperty(PsiModifier.FINAL));
  }
}
