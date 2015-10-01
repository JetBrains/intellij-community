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

/*
 * @author max
 */
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

public class AllClassesSearch extends ExtensibleQueryFactory<PsiClass, AllClassesSearch.SearchParameters> {
  public static final ExtensionPointName<QueryExecutor> EP_NAME = ExtensionPointName.create("com.intellij.allClassesSearch");
  public static final AllClassesSearch INSTANCE = new AllClassesSearch();

  public static class SearchParameters {
    private final SearchScope myScope;
    private final Project myProject;
    private final Condition<String> myShortNameCondition;

    public SearchParameters(@NotNull SearchScope scope, @NotNull Project project) {
      this(scope, project, Conditions.<String>alwaysTrue());
    }

    public SearchParameters(@NotNull SearchScope scope, @NotNull Project project, @NotNull Condition<String> shortNameCondition) {
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
  public static Query<PsiClass> search(@NotNull SearchScope scope, @NotNull Project project, @NotNull Condition<String> shortNameCondition) {
    return INSTANCE.createQuery(new SearchParameters(scope, project, shortNameCondition));
  }
}