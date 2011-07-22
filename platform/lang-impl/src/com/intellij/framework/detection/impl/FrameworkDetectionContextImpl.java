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
package com.intellij.framework.detection.impl;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetConfiguration;
import com.intellij.facet.FacetType;
import com.intellij.facet.impl.DefaultFacetsProvider;
import com.intellij.framework.detection.DetectedFrameworkDescription;
import com.intellij.framework.detection.FrameworkDetectionContext;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.FacetsProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMapBasedOnSet;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
public class FrameworkDetectionContextImpl implements FrameworkDetectionContext {
  private final Project myProject;

  public FrameworkDetectionContextImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public Project getProject() {
    return myProject;
  }

  @NotNull
  @Override
  public FacetsProvider getFacetsProvider() {
    return DefaultFacetsProvider.INSTANCE;
  }

  @NotNull
  @Override
  public <F extends Facet, C extends FacetConfiguration> List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull final FacetType<F, C> facetType,
                                                                                                                     @NotNull Collection<VirtualFile> files) {
    return createDetectedFacetDescriptions(facetType, files, new FacetConfigurationCreator<F, C>() {
      @NotNull
      @Override
      public List<Pair<C,Collection<VirtualFile>>> createConfigurations(@NotNull Collection<VirtualFile> files,
                                                                        @NotNull Collection<F> existentFacets) {
        return Collections.singletonList(Pair.create(facetType.createDefaultConfiguration(), files));
      }
    });
  }

  @NotNull
  @Override
  public <F extends Facet, C extends FacetConfiguration> List<? extends DetectedFrameworkDescription> createDetectedFacetDescriptions(@NotNull FacetType<F, C> facetType,
                                                                                                                     @NotNull Collection<VirtualFile> files,
                                                                                                                     @NotNull FacetConfigurationCreator<F, C> creator) {
    MultiMapBasedOnSet<Module, VirtualFile> filesByModule = new MultiMapBasedOnSet<Module, VirtualFile>();
    for (VirtualFile file : files) {
      final Module module = ModuleUtil.findModuleForFile(file, myProject);
      if (module != null) {
        filesByModule.putValue(module, file);
      }
    }
    final List<DetectedFrameworkDescription> result = new ArrayList<DetectedFrameworkDescription>();
    final FacetsProvider provider = getFacetsProvider();
    for (Module module : filesByModule.keySet()) {
      final Collection<F> facets = provider.getFacetsByType(module, facetType.getId());
      if (facetType.isOnlyOneFacetAllowed() && !facets.isEmpty()) {
        continue;
      }
      final List<Pair<C, Collection<VirtualFile>>> pairs = creator.createConfigurations(files, facets);
      for (Pair<C, Collection<VirtualFile>> pair : pairs) {
        result.add(new FacetBasedDetectedFrameworkDescription<C>(module, pair.getFirst(), new HashSet<VirtualFile>(pair.getSecond()), facetType));
      }
    }
    return result;
  }
}
