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

package com.intellij.facet;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class FacetManagerAdapter implements FacetManagerListener {
  @Override
  public void beforeFacetAdded(@NotNull Facet facet) {
  }

  @Override
  public void beforeFacetRemoved(@NotNull Facet facet) {
  }

  @Override
  public void beforeFacetRenamed(@NotNull final Facet facet) {
  }

  @Override
  public void facetRenamed(@NotNull final Facet facet, @NotNull final String oldName) {
  }

  @Override
  public void facetAdded(@NotNull Facet facet) {
  }

  @Override
  public void facetRemoved(@NotNull Facet facet) {
  }

  @Override
  public void facetConfigurationChanged(@NotNull Facet facet) {
  }
}
