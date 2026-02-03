// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.facet.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class FacetLibrariesValidatorDescription {
  private final String myDefaultLibraryName;

  public FacetLibrariesValidatorDescription(final @NonNls String defaultLibraryName) {
    myDefaultLibraryName = defaultLibraryName;
  }

  public @NonNls String getDefaultLibraryName() {
    return myDefaultLibraryName;
  }


  public void onLibraryAdded(final Facet facet, @NotNull Library library) {
  }
}
