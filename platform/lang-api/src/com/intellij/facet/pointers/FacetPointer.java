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

package com.intellij.facet.pointers;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.roots.ui.configuration.ModulesProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public interface FacetPointer<F extends Facet> {

  @NotNull
  Project getProject();

  @Nullable
  F getFacet();

  @NotNull
  String getModuleName();

  @NotNull
  String getFacetName();

  @NotNull
  String getId();

  @Nullable
  FacetType<F, ?> getFacetType();

  @Nullable
  F findFacet(@NotNull ModulesProvider modulesProvider, @NotNull FacetsProvider facetsProvider);

  @NotNull
  String getFacetTypeId();

  @NotNull
  String getFacetName(@NotNull ModulesProvider modulesProvider, @NotNull FacetsProvider facetsProvider);

  @NotNull 
  String getModuleName(@Nullable ModifiableModuleModel moduleModel);
}
