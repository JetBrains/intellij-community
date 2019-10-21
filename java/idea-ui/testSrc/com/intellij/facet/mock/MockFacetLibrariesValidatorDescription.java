// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.facet.mock;

import com.intellij.facet.Facet;
import com.intellij.facet.ui.libraries.FacetLibrariesValidatorDescription;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

/**
 * @author nik
 */
public class MockFacetLibrariesValidatorDescription extends FacetLibrariesValidatorDescription {
  private final Set<Library> myAddedLibraries = new HashSet<>();

  public MockFacetLibrariesValidatorDescription(final @NonNls String defaultLibraryName) {
    super(defaultLibraryName);
  }

  @Override
  public void onLibraryAdded(final Facet facet, @NotNull final Library library) {
    myAddedLibraries.add(library);
  }

}
