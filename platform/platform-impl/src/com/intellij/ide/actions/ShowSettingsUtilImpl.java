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
package com.intellij.ide.actions;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.*;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author max
 */
public class ShowSettingsUtilImpl extends ShowSettingsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowSettingsUtilImpl");
  @NonNls
  private static final String PREFER_CLASSIC_OPTIONS_EDITOR = "PREFER_CLASSIC_OPTIONS_EDITOR";

  public void showSettingsDialog(Project project, ConfigurableGroup[] group) {
    _showSettingsDialog(project, group, null);
  }

  private void _showSettingsDialog(final Project project, ConfigurableGroup[] group, Configurable toSelect) {
    group = filterEmptyGroups(group);

    if ("false".equalsIgnoreCase(System.getProperty("new.options.editor"))) {
      if (Boolean.toString(true).equals(PropertiesComponent.getInstance().getValue(PREFER_CLASSIC_OPTIONS_EDITOR))) {
        showExplorerOptions(project, group);
      }
      else {
        showControlPanelOptions(project, group, toSelect);
      }
    } else {
      new OptionsEditorDialog(project, group, toSelect).show();
    }
  }

  public void showSettingsDialog(@Nullable final Project project, final Class configurableClass) {
    assert Configurable.class.isAssignableFrom(configurableClass) : "Not a configurable: " + configurableClass.getName();

    Project actualProject = project != null ? project  : ProjectManager.getInstance().getDefaultProject();
    Configurable config = (Configurable)actualProject.getComponent(configurableClass);
    if (config == null) {
      config = (Configurable)ApplicationManager.getApplication().getComponent(configurableClass);
    }

    assert config != null : "Cannot find configurable: " + configurableClass.getName();

    showSettingsDialog(actualProject, config);
  }

  public void showSettingsDialog(@Nullable final Project project, final @NotNull String nameToSelect) {
    ConfigurableGroup[] group;
    if (project == null) {
      group = new ConfigurableGroup[] {new IdeConfigurablesGroup()};
    } else {
      group = new ConfigurableGroup[] {new ProjectConfigurablesGroup(project, false), new IdeConfigurablesGroup()};
    }

    Project actualProject = project != null ? project  : ProjectManager.getInstance().getDefaultProject();

    group = filterEmptyGroups(group);

    OptionsEditorDialog dialog = new OptionsEditorDialog(actualProject, group, nameToSelect);
    dialog.show();

  }

  public void showSettingsDialog(@NotNull final Project project, final Configurable toSelect) {
    _showSettingsDialog(project, new ConfigurableGroup[]{
      new ProjectConfigurablesGroup(project, false),
      new IdeConfigurablesGroup()
    }, toSelect);
  }

  private static ConfigurableGroup[] filterEmptyGroups(final ConfigurableGroup[] group) {
    List<ConfigurableGroup> groups = new ArrayList<ConfigurableGroup>();
    for (ConfigurableGroup g : group) {
      if (g.getConfigurables().length > 0) {
        groups.add(g);
      }
    }
    return groups.toArray(new ConfigurableGroup[groups.size()]);
  }

  public static void showControlPanelOptions(Project project, ConfigurableGroup[] groups, Configurable preselectedConfigurable) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, Boolean.toString(false));

    ControlPanelSettingsEditor editor = new ControlPanelSettingsEditor(project, groups, preselectedConfigurable);
    editor.show();
  }

  public static void showExplorerOptions(Project project, ConfigurableGroup[] group) {
    PropertiesComponent.getInstance().setValue(PREFER_CLASSIC_OPTIONS_EDITOR, Boolean.toString(true));
    ExplorerSettingsEditor editor = new ExplorerSettingsEditor(project, group);
    editor.show();
  }

  public boolean editConfigurable(Project project, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable, createDimensionKey(configurable));
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public <T extends Configurable> T findApplicationConfigurable(final Class<T> confClass) {
    return selectConfigurable(confClass, ApplicationManager.getApplication().getExtensions(Configurable.APPLICATION_CONFIGURABLES));
  }

  public <T extends Configurable> T findProjectConfigurable(final Project project, final Class<T> confClass) {
    return selectConfigurable(confClass, project.getExtensions(Configurable.PROJECT_CONFIGURABLES));
  }

  private static <T extends Configurable> T selectConfigurable(final Class<T> confClass, final Configurable... configurables) {
    for (Configurable configurable : configurables) {
      if (confClass.isAssignableFrom(configurable.getClass())) return (T)configurable;
    }

    throw new IllegalStateException("Can't find configurable of class " + confClass.getName());
  }

  public boolean editConfigurable(Project project, String dimensionServiceKey, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(project, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  @Override
  public boolean editProjectConfigurable(@NotNull Project project,
                                         Class<? extends Configurable> configurableClass,
                                         @NonNls String dimensionServiceKey) {
    final Configurable configurable = findProjectConfigurable(project, configurableClass);
    if (configurable == null) {
      LOG.error("Cannot find project configurable for " + configurableClass);
      return false;
    }
    if (dimensionServiceKey == null) {
      dimensionServiceKey = createDimensionKey(configurable);
    }
    return editConfigurable(project, dimensionServiceKey, configurable);
  }

  public boolean editConfigurable(Component parent, Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable, createDimensionKey(configurable));
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(final Component parent, final Configurable configurable, final Runnable advancedInitialization) {
    SingleConfigurableEditor editor = new SingleConfigurableEditor(parent, configurable, createDimensionKey(configurable));
    advancedInitialization.run();
    editor.show();
    return editor.isOK();
  }

  private static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }

  public boolean editConfigurable(Component parent, String dimensionServiceKey,Configurable configurable) {
    final SingleConfigurableEditor configurableEditor = new SingleConfigurableEditor(parent, configurable, dimensionServiceKey);
    configurableEditor.show();
    return configurableEditor.isOK();
  }

  public boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization) {
    SingleConfigurableEditor editor = new SingleConfigurableEditor(project, configurable, createDimensionKey(configurable));
    advancedInitialization.run();
    editor.show();
    return editor.isOK();
  }
}
