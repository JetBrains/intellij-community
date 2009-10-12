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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.util.ModificationTracker;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * @author nik
 */
public abstract class FacetFinder {

  public static FacetFinder getInstance(Project project) {
    return ServiceManager.getService(project, FacetFinder.class);
  }

  @Nullable
  public abstract <F extends Facet & FacetRootsProvider> F findFacet(VirtualFile file, FacetTypeId<F> type);

  @NotNull
  public abstract <F extends Facet & FacetRootsProvider> Collection<F> findFacets(VirtualFile file, FacetTypeId<F> type);

  public abstract <F extends Facet> ModificationTracker getAllFacetsOfTypeModificationTracker(FacetTypeId<F> type);
}
