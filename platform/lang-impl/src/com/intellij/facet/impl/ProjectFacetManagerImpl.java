// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.facet.impl;

import com.intellij.ProjectTopics;
import com.intellij.facet.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.ModuleListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@State(name = ProjectFacetManagerImpl.COMPONENT_NAME)
public final class ProjectFacetManagerImpl extends ProjectFacetManagerEx implements PersistentStateComponent<ProjectFacetManagerImpl.ProjectFacetManagerState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFacetManager";
  private static final Logger LOG = Logger.getInstance(ProjectFacetManagerImpl.class);
  private ProjectFacetManagerState myState = new ProjectFacetManagerState();
  private final Project myProject;
  private final AtomicReference<MultiMap<FacetTypeId<?>, Module>> myIndex = new AtomicReference<>();

  public ProjectFacetManagerImpl(Project project) {
    myProject = project;

    ProjectWideFacetListenersRegistry.getInstance(project).registerListener(new ProjectWideFacetAdapter<>() {
      @Override
      public void facetAdded(@NotNull Facet facet) {
        myIndex.set(null);
      }

      @Override
      public void facetRemoved(@NotNull Facet facet) {
        myIndex.set(null);
      }
    }, project);
    project.getMessageBus().connect().subscribe(ProjectTopics.MODULES, new ModuleListener() {
      @Override
      public void modulesAdded(@NotNull Project project, @NotNull List<? extends Module> modules) {
        myIndex.set(null);
      }

      @Override
      public void moduleRemoved(@NotNull Project project, @NotNull Module module) {
        myIndex.set(null);
      }
    });
  }

  @Override
  public ProjectFacetManagerState getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull final ProjectFacetManagerState state) {
    myState = state;
  }

  @NotNull
  private MultiMap<FacetTypeId<?>, Module> getIndex() {
    var index = myIndex.get();
    if (index != null) return index;
    return myIndex.updateAndGet(value -> value == null ? createIndex() : value);
  }

  @NotNull
  private MultiMap<FacetTypeId<?>, Module> createIndex() {
    MultiMap<FacetTypeId<?>, Module> index = MultiMap.createLinkedSet();
    for (Module module : ModuleManager.getInstance(myProject).getModules()) {
      Arrays.stream(FacetManager.getInstance(module).getAllFacets())
        .map(facet -> facet.getTypeId())
        .distinct()
        .forEach(facetTypeId -> index.putValue(facetTypeId, module));
    }
    return index;
  }

  @NotNull
  @Override
  public <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId) {
    return ContainerUtil.concat(getIndex().get(typeId), module -> FacetManager.getInstance(module).getFacetsByType(typeId));
  }

  @NotNull
  @Override
  public List<Module> getModulesWithFacet(@NotNull FacetTypeId<?> typeId) {
    return getIndex().get(typeId).stream().toList();
  }

  @Override
  public boolean hasFacets(@NotNull FacetTypeId<?> typeId) {
    return getIndex().containsKey(typeId);
  }

  @Override
  public <F extends Facet<?>> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules) {
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
