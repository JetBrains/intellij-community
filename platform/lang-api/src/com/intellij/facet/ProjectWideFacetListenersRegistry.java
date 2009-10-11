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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public abstract class ProjectWideFacetListenersRegistry {

  public static ProjectWideFacetListenersRegistry getInstance(Project project) {
    return ServiceManager.getService(project, ProjectWideFacetListenersRegistry.class);
  }

  public abstract <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener);
  public abstract <F extends Facet> void registerListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener,
                                                          @NotNull Disposable parentDisposable);
  public abstract <F extends Facet> void unregisterListener(@NotNull FacetTypeId<F> typeId, @NotNull ProjectWideFacetListener<? extends F> listener);

  public abstract void registerListener(@NotNull ProjectWideFacetListener<Facet> listener);
  public abstract void unregisterListener(@NotNull ProjectWideFacetListener<Facet> listener);
  public abstract void registerListener(@NotNull ProjectWideFacetListener<Facet> listener, @NotNull Disposable parentDisposable);
}
