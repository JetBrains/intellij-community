/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.framework.detection;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModel;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * @author nik
 */
public interface FrameworkDetectionContext {
  @NotNull
  Project getProject();

  @NotNull
  FacetsProvider getFacetsProvider();

  @NotNull
  <F extends Facet, C extends FacetConfiguration>
  List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetType<F, C> facetType, @NotNull Collection<VirtualFile> files);

  @NotNull
  <F extends Facet, C extends FacetConfiguration>
  List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetType<F, C> facetType, @NotNull Collection<VirtualFile> files,
                                                                               @NotNull FacetConfigurationCreator<F, C> creator);

  abstract class FacetConfigurationCreator<F extends Facet, C extends FacetConfiguration> {
    @NotNull
    public abstract List<Pair<C,Collection<VirtualFile>>> createConfigurations(@NotNull Collection<VirtualFile> files,
                                                                               @NotNull ModuleRootModel rootModel,
                                                                               @NotNull Collection<F> existentFacets);
  }
}
