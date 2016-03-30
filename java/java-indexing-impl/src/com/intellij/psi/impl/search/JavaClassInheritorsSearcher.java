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
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

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

    Collection<PsiClass> cached = HighlightingCaches.getInstance(project).ALL_SUB_CLASSES.get(parameters);
    if (cached == null) {
      cached = new ArrayList<>();
      boolean success = getAllSubClasses(project, baseClass, parameters, new CommonProcessors.CollectProcessor<>(cached));
      assert success;
      HighlightingCaches.getInstance(project).ALL_SUB_CLASSES.put(parameters, cached);
    }

    Processor<PsiClass> readActionedConsumer = ReadActionProcessor.wrapInReadAction(consumer);
    for (final PsiClass subClass : cached) {
      ProgressManager.checkCanceled();
      if (!readActionedConsumer.process(subClass)) {
        return false;
      }
    }
    return true;
  }

  private static boolean getAllSubClasses(@NotNull Project project,
                                          @NotNull PsiClass baseClass,
                                          @NotNull ClassInheritorsSearch.SearchParameters parameters,
                                          @NotNull Processor<PsiClass> consumer) {
    SearchScope searchScope = parameters.getScope();
    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<PsiAnchor> stack = new Stack<>();
    final Set<PsiAnchor> processed = ContainerUtil.newTroveSet();

    final Processor<PsiClass> processor = new ReadActionProcessor<PsiClass>() {
      @Override
      public boolean processInReadAction(PsiClass candidate) {
        ProgressManager.checkCanceled();

        if (parameters.isCheckInheritance() || !(candidate instanceof PsiAnonymousClass)) {
          if (!candidate.isInheritor(currentBase.get(), false)) {
            return true;
          }
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
