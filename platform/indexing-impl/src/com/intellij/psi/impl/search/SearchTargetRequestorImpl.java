// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.model.ModelElement;
import com.intellij.model.ModelReference;
import com.intellij.model.search.DefaultModelReferenceSearchParameters;
import com.intellij.model.search.ModelReferenceSearch;
import com.intellij.model.search.ModelReferenceSearchParameters;
import com.intellij.model.search.SearchTargetRequestor;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Preprocessor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.psi.search.PsiSearchScopeUtil.restrictScopeTo;

public class SearchTargetRequestorImpl implements SearchTargetRequestor {

  private final @NotNull SearchRequestCollectorImpl myCollector;
  private final @NotNull ModelElement myTarget;

  private SearchScope mySearchScope;
  private FileType[] myFileTypes;

  public SearchTargetRequestorImpl(@NotNull SearchRequestCollectorImpl collector, @NotNull ModelElement target) {
    myCollector = collector;
    myTarget = target;
  }

  @NotNull
  @Override
  public SearchTargetRequestor setSearchScope(@NotNull SearchScope scope) {
    mySearchScope = scope;
    return this;
  }

  @NotNull
  @Override
  public SearchTargetRequestor restrictSearchScopeTo(@NotNull FileType... fileTypes) {
    myFileTypes = fileTypes;
    return this;
  }

  @Override
  public void search() {
    search(Preprocessor.id());
  }

  @Override
  public void search(@NotNull Preprocessor<ModelReference, ModelReference> preprocessor) {
    myCollector.searchSubQuery(ModelReferenceSearch.search(createParameters()), preprocessor);
  }

  @NotNull
  private SearchScope getSearchScope() {
    SearchScope baseScope = mySearchScope == null ? myCollector.getParameters().getOriginalSearchScope() : mySearchScope;
    if (myFileTypes != null && myFileTypes.length > 0) {
      return restrictScopeTo(baseScope, myFileTypes);
    }
    else {
      return baseScope;
    }
  }

  private ModelReferenceSearchParameters createParameters() {
    ModelReferenceSearchParameters parameters = myCollector.getParameters();
    SearchScope searchScope = getSearchScope();
    return new DefaultModelReferenceSearchParameters(
      parameters.getProject(),
      myTarget,
      searchScope,
      parameters.isIgnoreAccessScope()
    );
  }
}
