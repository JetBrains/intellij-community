/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.facet.ui.libraries;

import com.intellij.facet.Facet;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class FacetLibrariesValidatorDescription {
  private final String myDefaultLibraryName;

  public FacetLibrariesValidatorDescription(final @NonNls String defaultLibraryName) {
    myDefaultLibraryName = defaultLibraryName;
  }

  @NonNls
  public String getDefaultLibraryName() {
    return myDefaultLibraryName;
  }


  public void onLibraryAdded(final Facet facet, @NotNull Library library) {
  }
}
