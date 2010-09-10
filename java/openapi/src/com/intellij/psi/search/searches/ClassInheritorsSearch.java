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
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * @author max
 */
public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.searches.ClassInheritorsSearch");

  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();


  static {
    INSTANCE.registerExecutor(new QueryExecutor<PsiClass, SearchParameters>() {
      public boolean execute(final SearchParameters parameters, final Processor<PsiClass> consumer) {
        final PsiClass baseClass = parameters.getClassToProcess();
        final SearchScope searchScope = parameters.getScope();

        LOG.assertTrue(searchScope != null);

        ProgressIndicator progress = ProgressManager.getInstance().getProgressIndicator();
        if (progress != null) {
          progress.pushState();
          String className = baseClass.getName();
          progress.setText(className != null ?
                           PsiBundle.message("psi.search.inheritors.of.class.progress", className) :
                           PsiBundle.message("psi.search.inheritors.progress"));
        }

        boolean result = processInheritors(consumer, baseClass, searchScope, parameters);

        if (progress != null) {
          progress.popState();
        }

        return result;
      }
    });
  }

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    private final Condition<String> myNameCondition;

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Condition.TRUE);
    }

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance,
                            boolean includeAnonymous, final Condition<String> nameCondition) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      myIncludeAnonymous = includeAnonymous;
      myNameCondition = nameCondition;
    }

    @NotNull
    public PsiClass getClassToProcess() {
      return myClass;
    }

    public Condition<String> getNameCondition() {
      return myNameCondition;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean isIncludeAnonymous() {
      return myIncludeAnonymous;
    }
  }

  private ClassInheritorsSearch() {}

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, checkDeep, checkInheritance, includeAnonymous));
  }

  public static Query<PsiClass> search(@NotNull SearchParameters parameters) {
    return INSTANCE.createUniqueResultsQuery(parameters);
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    return search(aClass, scope, checkDeep, checkInheritance, true);
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep) {
    return search(aClass, scope, checkDeep, true);
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, aClass.getUseScope(), checkDeep);
  }

  public static Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, true);
  }

  private static boolean processInheritors(@NotNull final Processor<PsiClass> consumer,
                                           @NotNull final PsiClass baseClass,
                                           @NotNull final SearchScope searchScope,
                                           @NotNull final SearchParameters parameters) {
    if (baseClass instanceof PsiAnonymousClass) return true;

    if (isFinal(baseClass)) return true;

    final String qname = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      public String compute() {
        return baseClass.getQualifiedName();
      }
    });
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) {
      return AllClassesSearch.search(searchScope, baseClass.getProject(), parameters.getNameCondition()).forEach(new Processor<PsiClass>() {
        public boolean process(final PsiClass aClass) {
          ProgressManager.checkCanceled();
          final String qname1 = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
            @Nullable
            public String compute() {
              return aClass.getQualifiedName();
            }
          });
          if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname1)) {
            return true;
          }

          return consumer.process(aClass);
        }
      });
    }

    final Ref<PsiClass> currentBase = Ref.create(null);
    final Stack<PsiClass> stack = new Stack<PsiClass>();
    final Set<PsiClass> processed = new HashSet<PsiClass>();
    final Processor<PsiClass> processor = new Processor<PsiClass>() {
      public boolean process(final PsiClass candidate) {
        ProgressManager.checkCanceled();

        final Ref<Boolean> result = new Ref<Boolean>();
        ApplicationManager.getApplication().runReadAction(new Runnable() {
          public void run() {
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
          stack.push(candidate);
        }

        return true;
      }
    };
    stack.push(baseClass);
    final GlobalSearchScope scope = GlobalSearchScope.allScope(baseClass.getProject());
    while (!stack.isEmpty()) {
      ProgressManager.checkCanceled();

      final PsiClass psiClass = stack.pop();
      if (!processed.add(psiClass)) continue;

      currentBase.set(psiClass);
      if (!DirectClassInheritorsSearch.search(psiClass, scope, parameters.isIncludeAnonymous()).forEach(processor)) return false;
    }
    return true;
  }

  private static boolean isFinal(final PsiClass baseClass) {
    return ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
      public Boolean compute() {
        return Boolean.valueOf(baseClass.hasModifierProperty(PsiModifier.FINAL));
      }
    }).booleanValue();
  }
}
