// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelElement;
import com.intellij.model.ModelReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ExtensibleQueryFactory;
import com.intellij.util.Query;
import org.jetbrains.annotations.NotNull;

public final class ModelReferenceSearch extends ExtensibleQueryFactory<ModelReference, ModelReferenceSearchParameters> {

  private static final ModelReferenceSearch INSTANCE = new ModelReferenceSearch();

  private ModelReferenceSearch() {}

  @NotNull
  public static Query<ModelReference> search(@NotNull Project project, @NotNull ModelElement target) {
    return search(project, target, GlobalSearchScope.allScope(project));
  }

  @NotNull
  public static Query<ModelReference> search(@NotNull Project project, @NotNull ModelElement target, @NotNull SearchScope searchScope) {
    return search(project, target, searchScope, false);
  }

  @NotNull
  public static Query<ModelReference> search(@NotNull Project project,
                                             @NotNull ModelElement target,
                                             @NotNull SearchScope searchScope,
                                             boolean ignoreAccessScope) {
    return search(new DefaultModelReferenceSearchParameters(project, target, searchScope, ignoreAccessScope));
  }

  @NotNull
  public static Query<ModelReference> search(@NotNull ModelReferenceSearchParameters parameters) {
    return new ModelReferenceSearchQuery(parameters, INSTANCE.createQuery(parameters));
  }
}
