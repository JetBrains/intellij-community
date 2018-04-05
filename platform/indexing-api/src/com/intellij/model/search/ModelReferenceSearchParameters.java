// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.search;

import com.intellij.model.ModelElement;
import com.intellij.openapi.application.DumbAwareSearchParameters;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NotNull;

public interface ModelReferenceSearchParameters extends DumbAwareSearchParameters {

  @Override
  default boolean isQueryValid() {
    return getTarget().isValid();
  }

  @NotNull
  ModelElement getTarget();

  @NotNull
  SearchScope getOriginalSearchScope();

  boolean isIgnoreAccessScope();

  @NotNull
  SearchScope getEffectiveSearchScope();
}
