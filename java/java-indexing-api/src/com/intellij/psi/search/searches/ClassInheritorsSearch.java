// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Search for inheritors of given class.
 * <p/>
 * For given hierarchy
 * <pre>
 *   class A {}
 *   class B extends A {}
 *   class C extends B {}
 * </pre>
 * searching for inheritors of {@code A} with default {@code checkDeep=true} returns {@code B} and {@code C}.
 * <p/>
 * Use {@code checkDeep=false} or {@link DirectClassInheritorsSearch} to search for direct inheritors only.
 *
 * @see com.intellij.psi.util.InheritanceUtil
 */
public final class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, ClassInheritorsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.classInheritorsSearch");
  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  public static class SearchParameters implements QueryParameters {
    private final @NotNull PsiClass myClass;
    private final @NotNull SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    private final @NotNull Condition<? super String> myNameCondition;
    private final @NotNull Project myProject;

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean checkDeep, boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Conditions.alwaysTrue());
    }

    public SearchParameters(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean checkDeep, boolean checkInheritance,
                            boolean includeAnonymous, @NotNull Condition<? super String> nameCondition) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      assert checkInheritance;
      myIncludeAnonymous = includeAnonymous;
      myNameCondition = nameCondition;
      myProject = PsiUtilCore.getProjectInReadAction(myClass);
    }

    public @NotNull PsiClass getClassToProcess() {
      return myClass;
    }

    @Override
    public @NotNull Project getProject() {
      return myProject;
    }

    @Override
    public boolean isQueryValid() {
      return myClass.isValid();
    }

    public @NotNull Condition<? super String> getNameCondition() {
      return myNameCondition;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    public @NotNull SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean isIncludeAnonymous() {
      return myIncludeAnonymous;
    }

    @ApiStatus.Experimental
    public boolean shouldSearchInLanguage(@NotNull Language language) {
      return true;
    }

    @Override
    public String toString() {
      return "'"+myClass.getQualifiedName()+
             "' scope="+myScope+
             (myCheckDeep ? " (deep)" : "") +
             (myCheckInheritance ? " (check inheritance)":"") +
             (myIncludeAnonymous ? " (anonymous)":"")+
             (myNameCondition == Conditions.<String>alwaysTrue() ? "" : " condition: "+myNameCondition);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      SearchParameters that = (SearchParameters)o;

      if (myCheckDeep != that.myCheckDeep) return false;
      if (myCheckInheritance != that.myCheckInheritance) return false;
      if (myIncludeAnonymous != that.myIncludeAnonymous) return false;
      if (!myClass.equals(that.myClass)) return false;
      if (!myScope.equals(that.myScope)) return false;
      return myNameCondition.equals(that.myNameCondition);
    }

    @Override
    public int hashCode() {
      int result = myClass.hashCode();
      result = 31 * result + myScope.hashCode();
      result = 31 * result + (myCheckDeep ? 1 : 0);
      result = 31 * result + (myCheckInheritance ? 1 : 0);
      result = 31 * result + (myIncludeAnonymous ? 1 : 0);
      result = 31 * result + myNameCondition.hashCode();
      return result;
    }
  }

  private ClassInheritorsSearch() {
    super(EP_NAME);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass,
                                                @NotNull SearchScope scope,
                                                boolean checkDeep,
                                                boolean checkInheritance,
                                                boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, checkDeep, checkInheritance, includeAnonymous));
  }

  public static @NotNull Query<PsiClass> search(@NotNull SearchParameters parameters) {
    if (!parameters.isCheckDeep()) {
      Query<PsiClass> directQuery = DirectClassInheritorsSearch
        .search(new DirectClassInheritorsSearch.SearchParameters(parameters.getClassToProcess(), parameters.getScope(),
                                                                 parameters.isIncludeAnonymous(), true) {
          @Override
          public boolean shouldSearchInLanguage(@NotNull Language language) {
            return parameters.shouldSearchInLanguage(language);
          }

          @Override
          public ClassInheritorsSearch.SearchParameters getOriginalParameters() {
            return parameters;
          }
        });
      if (parameters.getNameCondition() != Conditions.<String>alwaysTrue()) {
        directQuery = new FilteredQuery<>(directQuery, psiClass -> parameters.getNameCondition()
          .value(ReadAction.compute(psiClass::getName)));
      }
      return AbstractQuery.wrapInReadAction(directQuery);
    }
    return INSTANCE.createUniqueResultsQuery(parameters, psiClass ->
      ReadAction.compute(() -> SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass)));
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, @NotNull SearchScope scope, boolean checkDeep) {
    return search(aClass, scope, checkDeep, true, true);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass, boolean checkDeep) {
    return search(aClass, ReadAction.compute(() -> {
      if (!aClass.isValid()) {
        throw new ProcessCanceledException();
      }
      PsiFile file = aClass.getContainingFile();
      return PsiSearchHelper.getInstance(aClass.getProject()).getUseScope(file != null ? file : aClass);
    }), checkDeep);
  }

  public static @NotNull Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, true);
  }
}
