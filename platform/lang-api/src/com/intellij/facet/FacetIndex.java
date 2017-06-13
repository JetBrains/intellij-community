/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows to find modules with facets of specified types in amortized O(1) time, without iterating over all modules.
 * 
 * @see FacetManager
 * @since 173.*
 */
public class FacetIndex {
  private final MultiMap<FacetTypeId<?>, Module> myMap = MultiMap.createLinked();

  private FacetIndex(Project project) {
    for (Module module : ModuleManager.getInstance(project).getModules()) {
      for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
        myMap.putValue(facet.getTypeId(), module);
      }
    }
  }

  public boolean hasAnyModuleWithFacet(@NotNull FacetTypeId<?> type) {
    return myMap.containsKey(type);
  }

  @Nullable
  public Module findModuleWithFacet(@NotNull FacetTypeId<?> type) {
    return ContainerUtil.getFirstItem(myMap.get(type));
  }

  @NotNull
  public static FacetIndex getIndex(@NotNull Project project) {
    return CachedValuesManager.getManager(project).getCachedValue(project, () -> CachedValueProvider.Result.create(
      new FacetIndex(project), 
      ProjectRootModificationTracker.getInstance(project)));
  }
}
