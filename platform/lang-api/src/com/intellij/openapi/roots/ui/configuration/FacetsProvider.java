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

package com.intellij.openapi.roots.ui.configuration;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetTypeId;
import com.intellij.openapi.module.Module;

import java.util.Collection;

/**
 * @author nik
 */
public interface FacetsProvider {
  
  @NotNull
  Facet[] getAllFacets(Module module);

  @NotNull
  <F extends Facet> Collection<F> getFacetsByType(Module module, FacetTypeId<F> type);

  @Nullable
  <F extends Facet> F findFacet(Module module, FacetTypeId<F> type, String name);
}
