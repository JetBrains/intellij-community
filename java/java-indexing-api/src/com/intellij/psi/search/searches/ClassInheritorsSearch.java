// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class ClassInheritorsSearch extends ExtensibleQueryFactory<PsiClass, ClassInheritorsSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, ClassInheritorsSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.classInheritorsSearch");
  public static final ClassInheritorsSearch INSTANCE = new ClassInheritorsSearch();

  public static class SearchParameters implements QueryParameters {
    @NotNull
    private final PsiClass myClass;
    @NotNull
    private final SearchScope myScope;
    private final boolean myCheckDeep;
    private final boolean myCheckInheritance;
    private final boolean myIncludeAnonymous;
    @NotNull
    private final Condition<String> myNameCondition;
    @NotNull
    private final Project myProject;

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
      this(aClass, scope, checkDeep, checkInheritance, includeAnonymous, Conditions.alwaysTrue());
    }

    public SearchParameters(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance,
                            boolean includeAnonymous, @NotNull final Condition<String> nameCondition) {
      myClass = aClass;
      myScope = scope;
      myCheckDeep = checkDeep;
      myCheckInheritance = checkInheritance;
      assert checkInheritance;
      myIncludeAnonymous = includeAnonymous;
      myNameCondition = nameCondition;
      myProject = PsiUtilCore.getProjectInReadAction(myClass);
    }

    @NotNull
    public PsiClass getClassToProcess() {
      return myClass;
    }

    @NotNull
    @Override
    public Project getProject() {
      return myProject;
    }

    @Override
    public boolean isQueryValid() {
      return myClass.isValid();
    }

    @NotNull
    public Condition<String> getNameCondition() {
      return myNameCondition;
    }

    public boolean isCheckDeep() {
      return myCheckDeep;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }

    public boolean isCheckInheritance() {
      return myCheckInheritance;
    }

    public boolean isIncludeAnonymous() {
      return myIncludeAnonymous;
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

  @NotNull
  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance, boolean includeAnonymous) {
    return search(new SearchParameters(aClass, scope, checkDeep, checkInheritance, includeAnonymous));
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull SearchParameters parameters) {
    if (!parameters.isCheckDeep()) {
      Query<PsiClass> directQuery = DirectClassInheritorsSearch.search(parameters.getClassToProcess(), parameters.getScope(), parameters.isIncludeAnonymous());
      if (parameters.getNameCondition() != Conditions.<String>alwaysTrue()) {
        directQuery = new FilteredQuery<>(directQuery, psiClass -> parameters.getNameCondition()
          .value(ReadAction.compute(psiClass::getName)));
      }
      return AbstractQuery.wrapInReadAction(directQuery);
    }
    return INSTANCE.createUniqueResultsQuery(parameters, ContainerUtil.canonicalStrategy(),
                                             psiClass -> ReadAction.compute(() -> SmartPointerManager.getInstance(psiClass.getProject()).createSmartPsiElementPointer(psiClass)));
  }

  /**
   * @deprecated use {@link #search(PsiClass, SearchScope, boolean)} instead
   */
  @NotNull
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2020.2")
  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep, final boolean checkInheritance) {
    DeprecatedMethodException.report("Use ClassInheritorsSearch.search(PsiClass, SearchScope, boolean, boolean, boolean) instead");
    return search(aClass, scope, checkDeep, checkInheritance, true);
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull final PsiClass aClass, @NotNull SearchScope scope, final boolean checkDeep) {
    return search(aClass, scope, checkDeep, true, true);
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull final PsiClass aClass, final boolean checkDeep) {
    return search(aClass, ReadAction.compute(() -> {
      if (!aClass.isValid()) {
        throw new ProcessCanceledException();
      }
      PsiFile file = aClass.getContainingFile();
      return (file != null ? file : aClass).getUseScope();
    }), checkDeep);
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull PsiClass aClass) {
    return search(aClass, true);
  }
}
