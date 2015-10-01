/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Function;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.classInheritorsSearch");
  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  public static class SearchParameters {
    private final PsiClass myClass;
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    private final Condition<String> myNameCondition;

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Conditions.<String>alwaysTrue());
    }

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance,
                            boolean includeAnonymous, @NotNull final Condition<String> nameCondition) {
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
    return INSTANCE.createUniqueResultsQuery(parameters, ContainerUtil.<SmartPsiElementPointer<PsiClass>>canonicalStrategy(), new Function<PsiClass, SmartPsiElementPointer<PsiClass>>() {
      @Override
      public SmartPsiElementPointer<PsiClass> fun(final PsiClass psiClass) {
        return ApplicationManager.getApplication().runReadAction(new Computable<SmartPsiElementPointer<PsiClass>>() {
          @Override
          public SmartPsiElementPointer<PsiClass> compute() {
            return SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass);
          }
        });
      }
    });
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    return search(aClass, scope, checkDeep, checkInheritance, true);
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep) {
    return search(aClass, scope, checkDeep, true);
  }

  public static Query<PsiClass> search(@NotNull final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, ApplicationManager.getApplication().runReadAction(new Computable<SearchScope>() {
      @Override
      public SearchScope compute() {
        if (!aClass.isValid()) {
          throw new ProcessCanceledException();
        }
        return aClass.getUseScope();
      }
    }), checkDeep);
  }

  public static Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, true);
  }

}
