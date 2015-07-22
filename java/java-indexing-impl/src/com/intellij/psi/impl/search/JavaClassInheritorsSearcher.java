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
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.AllClassesSearch;
import com.intellij.psi.search.searches.ClassInheritorsSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
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

    processInheritors(consumer, baseClass, searchScope, parameters);

    if (progress != null) {
      progress.popState();
    }
  }

  private static void processInheritors(@NotNull final Processor<PsiClass> consumer,
                                           @NotNull final PsiClass baseClass,
                                           @NotNull final SearchScope searchScope,
                                           @NotNull final ClassInheritorsSearch.SearchParameters parameters) {
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return;

    final String qname = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return baseClass.getQualifiedName();
      }
    });
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) {
      Project project = PsiUtilCore.getProjectInReadAction(baseClass);
      AllClassesSearch.search(searchScope, project, parameters.getNameCondition()).forEach(new Processor<PsiClass>() {
        @Override
        public boolean process(final PsiClass aClass) {
          ProgressIndicatorProvider.checkCanceled();
          final String qname1 = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Override
            @Nullable
            public String compute() {
              return aClass.getQualifiedName();
            }
          });
          return CommonClassNames.JAVA_LANG_OBJECT.equals(qname1) || consumer.process(aClass);
        }
      });
      return;
    }

    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<Pair<Reference<PsiClass>, String>> stack = new Stack<Pair<Reference<PsiClass>, String>>();
    // there are two sets for memory optimization: it's cheaper to hold FQN than PsiClass
    final Set<String> processedFqns = new THashSet<String>(); // FQN of processed classes if the class has one
    final Set<PsiClass> processed = new THashSet<PsiClass>();   // processed classes without FQN (e.g. anonymous)

    final Processor<PsiClass> processor = new Processor<PsiClass>() {
      @Override
      public boolean process(final PsiClass candidate) {
        ProgressIndicatorProvider.checkCanceled();

        final Ref<Boolean> result = new Ref<Boolean>();
        final String[] fqn = new String[1];
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          @Override
          public void run() {
            fqn[0] = candidate.getQualifiedName();
            if (parameters.isCheckInheritance() || parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass)) {
              if (!candidate.isInheritor(currentBase.get(), false)) {
                result.set(true);
                return;
              }
            }

            if (PsiSearchScopeUtil.isInScope(searchScope, candidate)) {
              if (candidate instanceof PsiAnonymousClass) {
                result.set(consumer.process(candidate));
              }
              else {
                final String name = candidate.getName();
                if (name != null && parameters.getNameCondition().value(name) && !consumer.process(candidate)) result.set(false);
              }
            }
          }
        });
        if (!result.isNull()) return result.get().booleanValue();

        if (parameters.isCheckDeep() && !(candidate instanceof PsiAnonymousClass) && !isFinal(candidate)) {
          Reference<PsiClass> ref = fqn[0] == null ? createHardReference(candidate) : new SoftReference<PsiClass>(candidate);
          stack.push(Pair.create(ref, fqn[0]));
        }

        return true;
      }
    };
    stack.push(Pair.create(createHardReference(baseClass), qname));
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(PsiUtilCore.getProjectInReadAction(baseClass));
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(projectScope.getProject());
    while (!stack.isEmpty()) {
      ProgressIndicatorProvider.checkCanceled();

      Pair<Reference<PsiClass>, String> pair = stack.pop();
      PsiClass psiClass = pair.getFirst().get();
      final String fqn = pair.getSecond();
      if (psiClass == null) {
        psiClass = ApplicationManager.getApplication().runReadAction(new Computable<PsiClass>() {
          @Override
          public PsiClass compute() {
            return facade.findClass(fqn, projectScope);
          }
        });
        if (psiClass == null) continue;
      }
      if (fqn == null) {
        if (!processed.add(psiClass)) continue;
      }
      else {
        if (!processedFqns.add(fqn)) continue;
      }

      currentBase.set(psiClass);
      if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) return;
    }
  }

  private static Reference<PsiClass> createHardReference(final PsiClass candidate) {
    return new SoftReference<PsiClass>(candidate){
      @Override
      public PsiClass get() {
        return candidate;
      }
    };
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
