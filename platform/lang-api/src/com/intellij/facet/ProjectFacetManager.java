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

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApiStatus.NonExtendable
public abstract class ProjectFacetManager {

  public static ProjectFacetManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, ProjectFacetManager.class);
  }

  public abstract boolean hasFacets(@NotNull FacetTypeId<?> typeId);

  public abstract <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules);

  @NotNull
  public abstract <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId);

  @NotNull
  public abstract List<Module> getModulesWithFacet(@NotNull FacetTypeId<?> typeId);

  public abstract <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull FacetType<?, C> facetType);

  public abstract <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull FacetType<?, C> facetType, @NotNull C configuration);
}
