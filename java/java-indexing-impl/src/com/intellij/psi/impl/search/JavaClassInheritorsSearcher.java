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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.QueryExecutorBase;
import com.intellij.openapi.application.ReadActionProcessor;
import com.intellij.openapi.diagnostic.Logger;
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
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class JavaClassInheritorsSearcher extends QueryExecutorBase<PsiClass, ClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.search.JavaClassInheritorsSearcher");
  
  @Override
  public void processQuery(@NotNull ClassInheritorsSearch.SearchParameters parameters, @NotNull Processor<PsiClass> consumer) {
    final PsiClass baseClass = parameters.getClassToProcess();
    final SearchScope searchScope = parameters.getScope();

    LOG.assertTrue(searchScope != null);

    ProgressIndicator progress = ProgressIndicatorProvider.getGlobalProgressIndicator();
    if (progress != null) {
      progress.pushState();
      String className = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
        @Override
        public String compute() {
          return baseClass.getName();
        }
      });
      progress.setText(className != null ?
                       PsiBundle.message("psi.search.inheritors.of.class.progress", className) :
                       PsiBundle.message("psi.search.inheritors.progress"));
    }

    try {
      processInheritors(consumer, baseClass, searchScope, parameters);
    }
    finally {
      if (progress != null) {
        progress.popState();
      }
    }

  }

  private static void processInheritors(@NotNull final Processor<PsiClass> consumer,
                                           @NotNull final PsiClass baseClass,
                                           @NotNull final SearchScope searchScope,
                                           @NotNull final ClassInheritorsSearch.SearchParameters parameters) {
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return;

    Project project = PsiUtilCore.getProjectInReadAction(baseClass);
    if (isJavaLangObject(baseClass)) {
      AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(final PsiClass aClass) {
          ProgressManager.checkCanceled();
          return isJavaLangObject(aClass) || consumer.process(aClass);
        }
      });
      return;
    }

    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<PsiAnchor> stack = new Stack<PsiAnchor>();
    final Set<PsiAnchor> processed = ContainerUtil.newTroveSet();

    final Processor<PsiClass> processor = new ReadActionProcessor<PsiClass>() {
      @Override
      public boolean processInReadAction(PsiClass candidate) {
        ProgressManager.checkCanceled();

        if (parameters.isCheckInheritance() || parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass)) {
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

        if (parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass) && !isFinal(candidate)) {
          stack.push(PsiAnchor.create(candidate));
        }
        return true;
      }
    };
    
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        stack.push(PsiAnchor.create(baseClass));
      }
    });
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(project);
    
    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();

      final PsiAnchor anchor = stack.pop();
      if (!processed.add(anchor)) continue;

      PsiClass psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          public PsiClass compute() {
            return (PsiClass)anchor.retrieve();
          }
        });
      if (psiClass == null) continue;

      currentBase.set(psiClass);
      if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) return;
    }
  }

  private static boolean isJavaLangObject(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return baseClass.isValid() && CommonClassNames.JAVA_LANG_OBJECT.equals(baseClass.getQualifiedName());
      }
    });
  }

  private static boolean isFinal(@NotNull final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        return Boolean.valueOf(baseClass.hasModifierProperty(PsiModifier.FINAL));
      }
    }).booleanValue();
  }

}
