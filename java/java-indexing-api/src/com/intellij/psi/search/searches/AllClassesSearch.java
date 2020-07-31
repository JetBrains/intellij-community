// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

public final class AllClassesSearch extends ExtensibleQueryFactory<PsiClass, AllClassesSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor<PsiClass, AllClassesSearch.SearchParameters>> EP_NAME = ExtensionPointName.create("com.intellij.allClassesSearch");
  public static final AllClassesSearch INSTANCE = new AllClassesSearch();

  private AllClassesSearch() {
    super(EP_NAME);
  }

  public static class SearchParameters {
    private final SearchScope myScope;
    private final Project myProject;
    private final Condition<? super String> myShortNameCondition;

    public SearchParameters(@NotNull SearchScope scope, @NotNull Project project) {
      this(scope, project, Conditions.alwaysTrue());
    }

    public SearchParameters(@NotNull SearchScope scope, @NotNull Project project, @NotNull Condition<? super String> shortNameCondition) {
      myScope = scope;
      myProject = project;
      myShortNameCondition = shortNameCondition;
    }

    @NotNull
    public SearchScope getScope() {
      return myScope;
    }

    @NotNull
    public Project getProject() {
      return myProject;
    }

    public boolean nameMatches(@NotNull String name) {
      return myShortNameCondition.value(name);
    }
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull SearchScope scope, @NotNull Project project) {
    return INSTANCE.createQuery(new SearchParameters(scope, project));
  }

  @NotNull
  public static Query<PsiClass> search(@NotNull SearchScope scope, @NotNull Project project, @NotNull Condition<? super String> shortNameCondition) {
    return INSTANCE.createQuery(new SearchParameters(scope, project, shortNameCondition));
  }
}