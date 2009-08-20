package com.intellij.facet.impl;

import com.intellij.facet.*;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author nik
 */
@State(
    name = ProjectFacetManagerImpl.COMPONENT_NAME,
    storages = {
        @Storage(
            id="other",
            file="$PROJECT_FILE$"
        )
    }
)
public class ProjectFacetManagerImpl extends ProjectFacetManagerEx implements PersistentStateComponent<ProjectFacetManagerImpl.ProjectFacetManagerState> {
  @NonNls public static final String COMPONENT_NAME = "ProjectFacetManager";
  private static final Logger LOG = Logger.getInstance("#com.intellij.facet.impl.ProjectFacetManagerImpl");
  private ProjectFacetManagerState myState = new ProjectFacetManagerState();
  private final List<Runnable> myRunnablesToRunOnProjectSettingsClosed = new ArrayList<Runnable>();
  private Project myProject;

  public ProjectFacetManagerImpl(Project project) {
    myProject = project;
  }

  public ProjectFacetManagerState getState() {
    return myState;
  }

  public void loadState(final ProjectFacetManagerState state) {
    myState = state;
  }

  @Override
  public <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId) {
    return getFacets(typeId, ModuleManager.getInstance(myProject).getModules());
  }

  @Override
  public <F extends Facet> List<F> getFacets(@NotNull FacetTypeId<F> typeId, final Module[] modules) {
    final List<F> result = new ArrayList<F>();
    for (Module module : modules) {
      result.addAll(FacetManager.getInstance(module).getFacetsByType(typeId));
    }
    return result;
  }

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

  //todo[nik] remove
  public void registerRunnableToRunOnProjectSettingsClosed(@NotNull Runnable runnable) {
    myRunnablesToRunOnProjectSettingsClosed.add(runnable);
  }

  public void fireRunnableOnProjectSettingsClosed() {
    for (Runnable runnable : myRunnablesToRunOnProjectSettingsClosed) {
      runnable.run();
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
    private Map<String, DefaultFacetConfigurationState> myDefaultConfigurations = new HashMap<String, DefaultFacetConfigurationState>();

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
