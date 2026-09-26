// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search.searches;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiJavaModule;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Query;
import com.intellij.util.QueryExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows searching for Java (JPMS) modules declared in the project
 */
public final class JavaModuleSearch extends ExtensibleQueryFactory<PsiJavaModule, JavaModuleSearch.Parameters> {
  public static final ExtensionPointName<QueryExecutor<PsiJavaModule, JavaModuleSearch.Parameters>> EP_NAME = ExtensionPointName.create("com.intellij.javaModuleSearch");
  public static final JavaModuleSearch INSTANCE = new JavaModuleSearch();

  public static class Parameters {
    private final @Nullable String myName;
    private final @NotNull Project myProject;
    private final @NotNull GlobalSearchScope myScope;

    /**
     * @param name module name (null to find all modules)
     * @param project project
     * @param scope scope to search in
     */
    public Parameters(@Nullable String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
      myName = name;
      myProject = project;
      myScope = scope;
    }

    public @Nullable String getName() {
      return myName;
    }

    public @NotNull Project getProject() {
      return myProject;
    }

    public @NotNull GlobalSearchScope getScope() {
      return myScope;
    }
  }

  private JavaModuleSearch() {
    super(EP_NAME);
  }

  /**
   * Find JPMS modules in the scope
   * @param name name of the module to find
   * @param project project
   * @param scope scope to use
   * @return the query that contains found modules results
   */
  public static @NotNull Query<PsiJavaModule> search(@NotNull String name, @NotNull Project project, @NotNull GlobalSearchScope scope) {
    return INSTANCE.createQuery(new Parameters(name, project, scope));
  }

  /**
   * Find all JPMS modules in the scope
   * @param project project
   * @param scope scope to use
   * @return the query that contains found modules results
   */
  public static @NotNull Query<PsiJavaModule> allModules(@NotNull Project project, @NotNull GlobalSearchScope scope) {
    return INSTANCE.createQuery(new Parameters(null, project, scope));
  }
}
