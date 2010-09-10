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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.facet.Facet;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.options.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.roots.ui.configuration.artifacts.ArtifactsStructureConfigurable;
import com.intellij.openapi.roots.ui.configuration.projectRoot.*;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.ui.MasterDetailsComponent;
import com.intellij.openapi.ui.Splitter;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.ui.components.panels.Wrapper;
import com.intellij.ui.navigation.BackAction;
import com.intellij.ui.navigation.ForwardAction;
import com.intellij.ui.navigation.History;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ProjectStructureConfigurable extends BaseConfigurable implements SearchableConfigurable, Place.Navigator {

  public static final DataKey<ProjectStructureConfigurable> KEY = DataKey.create("ProjectStructureConfiguration");

  private final UIState myUiState = new UIState();
  private Splitter mySplitter;
  private JComponent myToolbarComponent;
  @NonNls private static final String CATEGORY = "category";
  private JComponent myToFocus;
  private boolean myWasUiDisposed;

  public static class UIState {
    public float proportion;
    public float sideProportion;

    public String lastEditedConfigurable;
  }

  private final Project myProject;
  private final ModuleManager myModuleManager;
  private final FacetStructureConfigurable myFacetStructureConfigurable;
  private final ArtifactsStructureConfigurable myArtifactsStructureConfigurable;

  private History myHistory = new History(this);
  private SidePanel mySidePanel;

  private JPanel myComponent;
  private final Wrapper myDetails = new Wrapper();

  private Configurable mySelectedConfigurable;

  private final ProjectSdksModel myProjectJdksModel = new ProjectSdksModel();

  private ProjectConfigurable myProjectConfig;
  private final ProjectLibrariesConfigurable myProjectLibrariesConfig;
  private final GlobalLibrariesConfigurable myGlobalLibrariesConfig;
  private ModuleStructureConfigurable myModulesConfig;

  private boolean myWasIntialized;

  private final List<Configurable> myName2Config = new ArrayList<Configurable>();
  private final StructureConfigurableContext myContext;
  private final ModulesConfigurator myModuleConfigurator;
  private JdkListConfigurable myJdkListConfig;

  private final JLabel myEmptySelection = new JLabel("<html><body><center>Select a setting to view or edit its details here</center></body></html>", JLabel.CENTER);

  public ProjectStructureConfigurable(final Project project, final ModuleManager moduleManager, final ProjectLibrariesConfigurable projectLibrariesConfigurable,
                                      final GlobalLibrariesConfigurable globalLibrariesConfigurable,
                                      final ModuleStructureConfigurable moduleStructureConfigurable,
                                      FacetStructureConfigurable facetStructureConfigurable,
                                      ArtifactsStructureConfigurable artifactsStructureConfigurable) {
    myProject = project;
    myModuleManager = moduleManager;
    myFacetStructureConfigurable = facetStructureConfigurable;
    myArtifactsStructureConfigurable = artifactsStructureConfigurable;

    myModuleConfigurator = new ModulesConfigurator(myProject, myProjectJdksModel);
    myContext = new StructureConfigurableContext(myProject, myModuleConfigurator);
    myModuleConfigurator.setContext(myContext);

    myProjectLibrariesConfig = projectLibrariesConfigurable;
    myGlobalLibrariesConfig = globalLibrariesConfigurable;
    myModulesConfig = moduleStructureConfigurable;
    
    myProjectLibrariesConfig.init(myContext);
    myGlobalLibrariesConfig.init(myContext);
    myModulesConfig.init(myContext);
    myFacetStructureConfigurable.init(myContext);
    myArtifactsStructureConfigurable.init(myContext, myModulesConfig, myProjectLibrariesConfig, myGlobalLibrariesConfig);

    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    myUiState.lastEditedConfigurable = propertiesComponent.getValue("project.structure.last.edited");
    final String proportion = propertiesComponent.getValue("project.structure.proportion");
    myUiState.proportion = proportion != null ? Float.parseFloat(proportion) : 0;
    final String sideProportion = propertiesComponent.getValue("project.structure.side.proportion");
    myUiState.sideProportion = sideProportion != null ? Float.parseFloat(sideProportion) : 0;
  }

  @NonNls
  public String getId() {
    return "project.structure";
  }

  @Nullable
  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return ProjectBundle.message("project.settings.display.name");
  }

  @Nullable
  public Icon getIcon() {
    return IconLoader.getIcon("/modules/modules.png");
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return mySelectedConfigurable != null ? mySelectedConfigurable.getHelpTopic() : "";
  }

  public JComponent createComponent() {
    myComponent = new MyPanel();

    mySplitter = new Splitter(false, .15f);
    mySplitter.setHonorComponentsMinimumSize(true);

    initSidePanel();

    final JPanel left = new JPanel(new BorderLayout()) {
      public Dimension getMinimumSize() {
        final Dimension original = super.getMinimumSize();
        return new Dimension(Math.max(original.width, 100), original.height);
      }
    };

    final DefaultActionGroup toolbarGroup = new DefaultActionGroup();
    toolbarGroup.add(new BackAction(myComponent));
    toolbarGroup.add(new ForwardAction(myComponent));
    final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(ActionPlaces.UNKNOWN, toolbarGroup, true);
    toolbar.setTargetComponent(myComponent);
    myToolbarComponent = toolbar.getComponent();
    left.add(myToolbarComponent, BorderLayout.NORTH);
    left.add(mySidePanel, BorderLayout.CENTER);

    mySplitter.setFirstComponent(left);
    mySplitter.setSecondComponent(myDetails);

    myComponent.add(mySplitter, BorderLayout.CENTER);

    myWasIntialized = true;

    return myComponent;
  }

  private void initSidePanel() {
    boolean isDefaultProject = myProject == ProjectManager.getInstance().getDefaultProject();

    mySidePanel = new SidePanel(this, myHistory);
    mySidePanel.addSeparator("Project Settings");
    addProjectConfig();
    if (!isDefaultProject) {
      addModulesConfig();
    }
    addProjectLibrariesConfig();

    if (!isDefaultProject) {
      addFacetsConfig();
      addArtifactsConfig();
    }
    mySidePanel.addSeparator("Platform Settings");
    addJdkListConfig();
    addGlobalLibrariesConfig();
  }

  private void addArtifactsConfig() {
    addConfigurable(myArtifactsStructureConfigurable);
  }

  public ArtifactsStructureConfigurable getArtifactsStructureConfigurable() {
    return myArtifactsStructureConfigurable;
  }

  private void addFacetsConfig() {
    if (FacetStructureConfigurable.isEnabled()) {
      addConfigurable(myFacetStructureConfigurable);
    }
  }

  private void addJdkListConfig() {
    if (myJdkListConfig == null) {
      myJdkListConfig = JdkListConfigurable.getInstance(myProject);
      myJdkListConfig.init(myContext);
    }
    addConfigurable(myJdkListConfig);
  }

  private void addProjectConfig() {
    myProjectConfig = new ProjectConfigurable(myProject, myModuleConfigurator, myProjectJdksModel);
    addConfigurable(myProjectConfig);
  }

  private void addProjectLibrariesConfig() {
    addConfigurable(myProjectLibrariesConfig);
  }

  private void addGlobalLibrariesConfig() {
    addConfigurable(myGlobalLibrariesConfig);
  }

  private void addModulesConfig() {
    myModulesConfig = ModuleStructureConfigurable.getInstance(myProject);
    addConfigurable(myModulesConfig);
  }

  public boolean isModified() {
    for (Configurable each : myName2Config) {
      if (each.isModified()) return true;
    }

    return false;
  }

  public void apply() throws ConfigurationException {
    for (Configurable each : myName2Config) {
      if (each.isModified()) {
        each.apply();
      }
    }

    myContext.getDaemonAnalyzer().clearCaches();
  }

  public void reset() {
    myWasUiDisposed = false;

    myContext.reset();
    
    myProjectJdksModel.reset(myProject);

    Configurable toSelect = null;
    for (Configurable each : myName2Config) {
      if (myUiState.lastEditedConfigurable != null && myUiState.lastEditedConfigurable.equals(each.getDisplayName())) {
        toSelect = each;
      }
      if (each instanceof MasterDetailsComponent) {
        ((MasterDetailsComponent)each).setHistory(myHistory);
      }
      each.reset();
    }

    myHistory.clear();

    if (toSelect == null && myName2Config.size() > 0) {
      toSelect = myName2Config.iterator().next();
    }

    removeSelected();

    navigateTo(toSelect != null ? createPlaceFor(toSelect) : null, false);

    if (myUiState.proportion > 0) {
      mySplitter.setProportion(myUiState.proportion);
    }
  }

  public void disposeUIResources() {
    if (!myWasIntialized) return;
    final PropertiesComponent propertiesComponent = PropertiesComponent.getInstance(myProject);
    propertiesComponent.setValue("project.structure.last.edited", myUiState.lastEditedConfigurable);
    propertiesComponent.setValue("project.structure.proportion", String.valueOf(myUiState.proportion));
    propertiesComponent.setValue("project.structure.side.proportion", String.valueOf(myUiState.sideProportion));

    myWasUiDisposed = true;

    myUiState.proportion = mySplitter.getProportion();
    saveSideProportion();
    myContext.getDaemonAnalyzer().stop();
    for (Configurable each : myName2Config) {
      each.disposeUIResources();
    }
    myContext.clear();
    myName2Config.clear();

    myModuleConfigurator.getFacetsConfigurator().clearMaps();
    myWasIntialized = false;
  }

  public History getHistory() {
    return myHistory;
  }

  public void setHistory(final History history) {
    myHistory = history;
  }

  public void queryPlace(@NotNull final Place place) {
    place.putPath(CATEGORY, mySelectedConfigurable);
    Place.queryFurther(mySelectedConfigurable, place);
  }

  public ActionCallback selectProjectGeneralSettings(final boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, myProjectConfig);
    return navigateTo(place, requestFocus);
  }

  public ActionCallback select(@Nullable final String moduleToSelect, String tab, final boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, myModulesConfig);
    if (moduleToSelect != null) {
      final Module module = ModuleManager.getInstance(myProject).findModuleByName(moduleToSelect);
      assert module != null;
      place = place.putPath(ModuleStructureConfigurable.TREE_OBJECT, module);
    }
    return navigateTo(place, requestFocus);
  }

  public ActionCallback select(@Nullable final Facet facetToSelect, final boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, myModulesConfig);
    if (facetToSelect != null) {
      place = place.putPath(ModuleStructureConfigurable.TREE_OBJECT, facetToSelect);
    }
    return navigateTo(place, requestFocus);
  }

  public ActionCallback select(@NotNull Sdk sdk, final boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, myJdkListConfig);
    place.putPath(BaseStructureConfigurable.TREE_NAME, sdk.getName());
    return navigateTo(place, requestFocus);
  }

  public ActionCallback selectProjectOrGlobalLibrary(@NotNull Library library, boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, getConfigurableFor(library));
    place.putPath(BaseStructureConfigurable.TREE_NAME, library.getName());
    return navigateTo(place, requestFocus);
  }

  public ActionCallback select(@Nullable Artifact artifact, boolean requestFocus) {
    Place place = new Place().putPath(CATEGORY, myArtifactsStructureConfigurable);
    if (artifact != null) {
      place.putPath(BaseStructureConfigurable.TREE_NAME, artifact.getName());
    }
    return navigateTo(place, requestFocus);
  }

  public ActionCallback select(@NotNull LibraryOrderEntry libraryOrderEntry, final boolean requestFocus) {
    final Library lib = libraryOrderEntry.getLibrary();
    if (lib == null || lib.getTable() == null) {
      Place place = new Place().putPath(CATEGORY, myModulesConfig);
      place.putPath(BaseStructureConfigurable.TREE_OBJECT, libraryOrderEntry.getOwnerModule());
      return navigateTo(place, requestFocus);
    }
    Place place = new Place().putPath(CATEGORY, getConfigurableFor(lib));
    place.putPath(BaseStructureConfigurable.TREE_NAME, libraryOrderEntry.getLibraryName());
    return navigateTo(place, requestFocus);
  }

  public ActionCallback navigateTo(@Nullable final Place place, final boolean requestFocus) {
    final Configurable toSelect = (Configurable)place.getPath(CATEGORY);

    JComponent detailsContent = myDetails.getTargetComponent();

    if (mySelectedConfigurable != toSelect) {
      saveSideProportion();
      removeSelected();

      if (toSelect != null) {
        detailsContent = toSelect.createComponent();
        myDetails.setContent(detailsContent);
      }

      mySelectedConfigurable = toSelect;
      if (mySelectedConfigurable != null) {
        myUiState.lastEditedConfigurable = mySelectedConfigurable.getDisplayName();
      }

      if (toSelect instanceof MasterDetailsComponent) {
        final MasterDetailsComponent masterDetails = (MasterDetailsComponent)toSelect;
        if (myUiState.sideProportion > 0) {
          masterDetails.getSplitter().setProportion(myUiState.sideProportion);
        }
        masterDetails.setHistory(myHistory);
      }

      if (toSelect instanceof DetailsComponent.Facade) {
        ((DetailsComponent.Facade)toSelect).getDetailsComponent().setBannerMinHeight(myToolbarComponent.getPreferredSize().height);
      }
    }



    if (detailsContent != null) {
      JComponent toFocus = IdeFocusTraversalPolicy.getPreferredFocusedComponent(detailsContent);
      if (toFocus == null) {
        toFocus = detailsContent;
      }
      if (requestFocus) {
        myToFocus = toFocus;
        UIUtil.requestFocus(toFocus);
      }
    }

    final ActionCallback result = new ActionCallback();
    Place.goFurther(toSelect, place, requestFocus).notifyWhenDone(result);

    myDetails.revalidate();
    myDetails.repaint();

    if (toSelect != null) {
      mySidePanel.select(createPlaceFor(toSelect));
    }

    if (!myHistory.isNavigatingNow() && mySelectedConfigurable != null) {
      myHistory.pushQueryPlace();
    }

    return result;
  }

  private void saveSideProportion() {
    if (mySelectedConfigurable instanceof MasterDetailsComponent) {
      myUiState.sideProportion = ((MasterDetailsComponent)mySelectedConfigurable).getSplitter().getProportion();
    }
  }

  private void removeSelected() {
    myDetails.removeAll();
    mySelectedConfigurable = null;
    myUiState.lastEditedConfigurable = null;

    myDetails.add(myEmptySelection, BorderLayout.CENTER);
  }

  public static ProjectStructureConfigurable getInstance(final Project project) {
    return ServiceManager.getService(project, ProjectStructureConfigurable.class);
  }

  public ProjectSdksModel getProjectJdksModel() {
    return myProjectJdksModel;
  }

  public JdkListConfigurable getJdkConfig() {
    return myJdkListConfig;
  }

  public ProjectLibrariesConfigurable getProjectLibrariesConfig() {
    return myProjectLibrariesConfig;
  }

  public GlobalLibrariesConfigurable getGlobalLibrariesConfig() {
    return myGlobalLibrariesConfig;
  }

  public ModuleStructureConfigurable getModulesConfig() {
    return myModulesConfig;
  }

  public ProjectConfigurable getProjectConfig() {
    return myProjectConfig;
  }

  private void addConfigurable(Configurable configurable) {
    myName2Config.add(configurable);

    mySidePanel.addPlace(createPlaceFor(configurable), new Presentation(configurable.getDisplayName()));
  }

  private static Place createPlaceFor(final Configurable configurable) {
    return new Place().putPath(CATEGORY, configurable);
  }


  public StructureConfigurableContext getContext() {
    return myContext;
  }

  private class MyPanel extends JPanel implements DataProvider {
    public MyPanel() {
      super(new BorderLayout());
    }

    @Nullable
    public Object getData(@NonNls final String dataId) {
      if (KEY.is(dataId)) {
        return ProjectStructureConfigurable.this;
      } else if (History.KEY.is(dataId)) {
        return getHistory();
      } else {
        return null;
      }
    }

    public Dimension getPreferredSize() {
      return new Dimension(1024, 768);
    }
  }

  public BaseLibrariesConfigurable getConfigurableFor(final Library library) {
    if (LibraryTablesRegistrar.PROJECT_LEVEL.equals(library.getTable().getTableLevel())) {
      return myProjectLibrariesConfig;
    } else {
      return myGlobalLibrariesConfig;
    }
  }

  public JComponent getPreferredFocusedComponent() {
    return myToFocus;
  }



}
