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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiSearchScopeUtil;
import com.intellij.psi.search.SearchScope;
import com.intellij.reference.SoftReference;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.Stack;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.Set;

/**
 * @author max
 */
public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.classInheritorsSearch");
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.search.searches.ClassInheritorsSearch");

  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  static {
    INSTANCE.registerExecutor(new QueryExecutor<PsiClass, SearchParameters>() {
      @Override
      public boolean execute(@NotNull final SearchParameters parameters, @NotNull final Processor<PsiClass> consumer) {
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

        boolean result = processInheritors(consumer, baseClass, searchScope, parameters);

        if (progress != null) {
          progress.popState();
        }

        return result;
      }
    });
  }

  public interface InheritanceChecker {
    boolean checkInheritance(@NotNull PsiClass subClass, @NotNull PsiClass parentClass);

    InheritanceChecker DEFAULT = new InheritanceChecker() {
      @Override
      public boolean checkInheritance(@NotNull PsiClass subClass, @NotNull PsiClass parentClass) {
        return subClass.isInheritor(parentClass, false);
      }
    };
  }

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    private final Condition<String> myNameCondition;
    private final InheritanceChecker myInheritanceChecker;

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Condition.TRUE);
    }

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance,
                            boolean includeAnonymous, @NotNull final Condition<String> nameCondition) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, nameCondition, InheritanceChecker.DEFAULT);
    }

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance,
                            boolean includeAnonymous, @NotNull final Condition<String> nameCondition, @NotNull InheritanceChecker inheritanceChecker) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      myIncludeAnonymous = includeAnonymous;
      myNameCondition = nameCondition;
      myInheritanceChecker = inheritanceChecker;
    }

    @NotNull
    public PsiClass getClassToProcess() {
      return myClass;
    }

    @NotNull public Condition<String> getNameCondition() {
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
    return INSTANCE.createQuery(parameters);
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
    if (baseClass instanceof PsiAnonymousClass || isFinal(baseClass)) return true;

    final String qname = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        return baseClass.getQualifiedName();
      }
    });
    if (CommonClassNames.JAVA_LANG_OBJECT.equals(qname)) {
      return AllClassesSearch.search(searchScope, baseClass.getProject(), parameters.getNameCondition()).forEach(new Processor<PsiClass>() {
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
              if (!parameters.myInheritanceChecker.checkInheritance(candidate, currentBase.get())) {
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
    final GlobalSearchScope projectScope = GlobalSearchScope.allScope(baseClass.getProject());
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
      if (!DirectClassInheritorsSearch.search(psiClass, projectScope, parameters.isIncludeAnonymous(), false).forEach(processor)) return false;
    }
    return true;
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
