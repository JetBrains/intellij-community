/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.ParameterizedCachedValue;
import com.intellij.psi.util.ParameterizedCachedValueProvider;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

/**
 * @author nik
 */
@State(name = ProjectFacetManagerImpl.COMPONENT_NAME)
public class ProjectFacetManagerImpl extends ProjectFacetManagerEx implements PersistentStateComponent<ProjectFacetManagerImpl.ProjectFacetManagerState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFacetManager";
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetManagerImpl");
  private ProjectFacetManagerState myState = new ProjectFacetManagerState();
  private final Project myProject;
  private final ConcurrentMap<FacetTypeId<?>, ParameterizedCachedValue<Boolean, FacetTypeId<?>>> myCachedHasFacets =
    ContainerUtil.newConcurrentMap();
  private final ParameterizedCachedValueProvider<Boolean,FacetTypeId<?>> myCachedValueProvider;

  public ProjectFacetManagerImpl(Project project) {
    myProject = project;
    myCachedValueProvider = new ParameterizedCachedValueProvider<Boolean, FacetTypeId<?>>() {
      @Override
      public CachedValueProvider.Result<Boolean> compute(FacetTypeId<?> param) {
        boolean result = false;
        for (Module module : ModuleManager.getInstance(myProject).getModules()) {
          if (!FacetManager.getInstance(module).getFacetsByType(param).isEmpty()) {
            result = true;
            break;
          }
        }
        return CachedValueProvider.Result.create(result, FacetFinder.getInstance(myProject).getAllFacetsOfTypeModificationTracker(param));
      }
    };
  }

  @Override
  public ProjectFacetManagerState getState() {
    return myState;
  }

  @Override
  public void loadState(final ProjectFacetManagerState state) {
    myState = state;
  }

  @NotNull
  @Override
  public <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId) {
    return getFacets(typeId, ModuleManager.getInstance(myProject).getModules());
  }

  @NotNull
  @Override
  public List<Module> getModulesWithFacet(@NotNull FacetTypeId<?> typeId) {
    List<Module> result = new ArrayList<>();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      if (!FacetManager.getInstance(module).getFacetsByType(typeId).isEmpty()) {
        result.add(module);
      }
    }
    return result;
  }

  @Override
  public boolean hasFacets(@NotNull FacetTypeId<?> typeId) {
    ParameterizedCachedValue<Boolean, FacetTypeId<?>> value = myCachedHasFacets.get(typeId);
    if (value == null) {
      value = CachedValuesManager.getManager(myProject).createParameterizedCachedValue(myCachedValueProvider, false);
      myCachedHasFacets.put(typeId, value);
    }
    return value.getValue(typeId);
  }

  @Override
  public <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules) {
    final List<F> result = new ArrayList<>();
    for (Module module : modules) {
      result.addAll(FacetManager.getInstance(module).getFacetsByType(typeId));
    }
    return result;
  }

  @Override
  public <C extends FacetConfiguration> C createDefaultConfiguration(@NotNull final FacetType<?, C> facetType) {
    C configuration = facetType.createDefaultConfiguration();
    DefaultFacetConfigurationState state = myState.getDefaultConfigurations().get(facetType.getStringId());
    if (state != null) {
      Element defaultConfiguration = state.getDefaultConfiguration();
      try {
        FacetUtil.loadFacetConfiguration(configuration, defaultConfiguration);
      }
      catch (InvalidDataException e) {
        LOG.info(e);
      }
    }
    return configuration;
  }

  @Override
  public <C extends FacetConfiguration> void setDefaultConfiguration(@NotNull final FacetType<?, C> facetType, @NotNull final C configuration) {
    Map<String, DefaultFacetConfigurationState> defaultConfigurations = myState.getDefaultConfigurations();
    DefaultFacetConfigurationState state = defaultConfigurations.get(facetType.getStringId());
    if (state == null) {
      state = new DefaultFacetConfigurationState();
      defaultConfigurations.put(facetType.getStringId(), state);
    }
    try {
      Element element = FacetUtil.saveFacetConfiguration(configuration);
      state.setDefaultConfiguration(element);
    }
    catch (WriteExternalException e) {
      LOG.info(e);
    }
  }

  @Tag("default-facet-configuration")
  public static class DefaultFacetConfigurationState {
    private Element myDefaultConfiguration;

    @Tag("configuration")
    public Element getDefaultConfiguration() {
      return myDefaultConfiguration;
    }

    public void setDefaultConfiguration(final Element defaultConfiguration) {
      myDefaultConfiguration = defaultConfiguration;
    }
  }

  public static class ProjectFacetManagerState {
    private Map<String, DefaultFacetConfigurationState> myDefaultConfigurations = new HashMap<>();

    @Tag("default-configurations")
    @MapAnnotation(surroundWithTag = false, surroundKeyWithTag = false, surroundValueWithTag = false, //entryTagName = "default-configuration",
                   keyAttributeName = "facet-type")
    public Map<String, DefaultFacetConfigurationState> getDefaultConfigurations() {
      return myDefaultConfigurations;
    }

    public void setDefaultConfigurations(final Map<String, DefaultFacetConfigurationState> defaultConfigurations) {
      myDefaultConfigurations = defaultConfigurations;
    }
  }
}
