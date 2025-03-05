// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiImplicitClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to search for implicit classes declared in the project
 */
public final class ImplicitClassSearch extends ExtensibleQueryFactory<PsiImplicitClass, ImplicitClassSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor<PsiImplicitClass, ImplicitClassSearch.Parameters>> EP_NAME = ExtensionPointName.create("com.intellij.implicitClassSearch");
  public static final ImplicitClassSearch INSTANCE = new ImplicitClassSearch();

  public static class Parameters {
    private final @NotNull String myName;
    private final @NotNull Project myProject;
    private final @NotNull GlobalSearchScope myScope;

    public Parameters(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      myName = name;
      myProject = project;
      myScope = scope;
    }

    public @NotNull String getName() {
      return myName;
    }

    public @NotNull Project getProject() {
      return myProject;
    }

    public @NotNull GlobalSearchScope getScope() {
      return myScope;
    }
  }

  private ImplicitClassSearch() {
    super(EP_NAME);
  }

  /**
   * Find implicit classes in the project
   * @param name name of the class to find
   * @param project project
   * @param scope scope to use
   * @return the query that contains implicit class results
   */
  public static @NotNull Query<PsiImplicitClass> search(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return INSTANCE.createQuery(new Parameters(name, project, scope));
  }
}
