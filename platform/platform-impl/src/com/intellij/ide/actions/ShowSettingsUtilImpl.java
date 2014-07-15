/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.IdeConfigurablesGroup;
import com.intellij.openapi.options.ex.MixedConfigurableGroup;
import com.intellij.openapi.options.ex.ProjectConfigurablesGroup;
import com.intellij.openapi.options.ex.SingleConfigurableEditor;
import com.intellij.openapi.options.newEditor.OptionsEditor;
import com.intellij.openapi.options.newEditor.OptionsEditorDialog;
import com.intellij.openapi.options.newEditor.PreferencesDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author max
 */
public class ShowSettingsUtilImpl extends ShowSettingsUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.actions.ShowSettingsUtilImpl");
  private AtomicBoolean myShown = new AtomicBoolean(false);

  @NotNull
  private static Project getProject(@Nullable Project project) {
    return project != null ? project : ProjectManager.getInstance().getDefaultProject();
  }

  @NotNull
  private static DialogWrapper getDialog(@Nullable Project project, @NotNull ConfigurableGroup[] groups, @Nullable Configurable toSelect) {
    return Registry.is("ide.perProjectModality")
           ? new OptionsEditorDialog(getProject(project), filterEmptyGroups(groups), toSelect, true)
           : Registry.is("ide.new.preferences")
             ? new PreferencesDialog(getProject(project), filterEmptyGroups(groups))
             : new OptionsEditorDialog(getProject(project), filterEmptyGroups(groups), toSelect);
  }

  @NotNull
  public static ConfigurableGroup[] getConfigurableGroups(@Nullable Project project, boolean withIdeSettings) {
    ConfigurableGroup[] groups = !withIdeSettings
           ? new ConfigurableGroup[]{new ProjectConfigurablesGroup(getProject(project))}
           : (project == null)
             ? new ConfigurableGroup[]{new IdeConfigurablesGroup()}
             : new ConfigurableGroup[]{
               new ProjectConfigurablesGroup(project),
               new IdeConfigurablesGroup()};

    return Registry.is("ide.file.settings.order.new")
           ? MixedConfigurableGroup.getGroups(getConfigurables(groups, true))
           : groups;
  }

  @NotNull
  public static Configurable[] getConfigurables(@Nullable Project project, boolean withGroupReverseOrder) {
    return getConfigurables(getConfigurableGroups(project, true), withGroupReverseOrder);
  }

  @NotNull
  private static Configurable[] getConfigurables(@NotNull ConfigurableGroup[] groups, boolean withGroupReverseOrder) {
    Configurable[][] arrays = new Configurable[groups.length][];
    int length = 0;
    for (int i = 0; i < groups.length; i++) {
      arrays[i] = groups[withGroupReverseOrder ? groups.length - 1 - i : i].getConfigurables();
      length += arrays[i].length;
    }
    Configurable[] configurables = new Configurable[length];
    int offset = 0;
    for (Configurable[] array : arrays) {
      System.arraycopy(array, 0, configurables, offset, array.length);
      offset += array.length;
    }
    return configurables;
  }

  @Override
  public void showSettingsDialog(@NotNull Project project, @NotNull ConfigurableGroup[] group) {
    try {
      myShown.set(true);
      getDialog(project, group, null).show();
    }
    catch (Exception e) {
      LOG.error(e);
    }
    finally {
      myShown.set(false);
    }
  }

  @Override
  public void showSettingsDialog(@Nullable final Project project, final Class configurableClass) {
    assert Configurable.class.isAssignableFrom(configurableClass) : "Not a configurable: " + configurableClass.getName();

    ConfigurableGroup[] groups = getConfigurableGroups(project, true);

    Configurable config = findByClass(getConfigurables(groups, true), configurableClass);

    assert config != null : "Cannot find configurable: " + configurableClass.getName();

    getDialog(project, groups, config).show();
  }

  @Nullable
  private static Configurable findByClass(Configurable[] configurables, Class configurableClass) {
    for (Configurable configurable : configurables) {
      if (configurableClass.isInstance(configurable)) {
        return configurable;
      }
    }
    return null;
  }

  @Override
  public void showSettingsDialog(@Nullable final Project project, @NotNull final String nameToSelect) {
    ConfigurableGroup[] group = getConfigurableGroups(project, true);

    Project actualProject = getProject(project);

    group = filterEmptyGroups(group);

    OptionsEditorDialog dialog;
    if (Registry.is("ide.perProjectModality")) {
      dialog = new OptionsEditorDialog(actualProject, group, nameToSelect, true);
    }
    else {
      dialog = new OptionsEditorDialog(actualProject, group, nameToSelect);
    }
    dialog.show();
  }

  public static void showSettingsDialog(@Nullable Project project, final String id2Select, final String filter) {
    ConfigurableGroup[] group = getConfigurableGroups(project, true);

    Project actualProject = getProject(project);

    group = filterEmptyGroups(group);
    final Configurable configurable2Select = findConfigurable2Select(id2Select, group);

    final OptionsEditorDialog dialog;
    if (Registry.is("ide.perProjectModality")) {
      dialog = new OptionsEditorDialog(actualProject, group, configurable2Select, true);
    } else {
      dialog = new OptionsEditorDialog(actualProject, group, configurable2Select);
    }

    new UiNotifyConnector.Once(dialog.getContentPane(), new Activatable.Adapter() {
      @Override
      public void showNotify() {
        final OptionsEditor editor = (OptionsEditor)dialog.getData(OptionsEditor.KEY.getName());
        LOG.assertTrue(editor != null);
        editor.select(configurable2Select, filter);
      }
    });
    dialog.show();
  }

  @Nullable
  private static Configurable findConfigurable2Select(String id2Select, ConfigurableGroup[] group) {
    for (ConfigurableGroup configurableGroup : group) {
      for (Configurable configurable : configurableGroup.getConfigurables()) {
        final Configurable conf = containsId(id2Select, configurable);
        if (conf != null) return conf;
      }
    }
    return null;
  }

  @Nullable
  private static Configurable containsId(String id2Select, Configurable configurable) {
    if (configurable instanceof SearchableConfigurable && id2Select.equals(((SearchableConfigurable)configurable).getId())) {
      return configurable;
    }
    if (configurable instanceof SearchableConfigurable.Parent) {
      for (Configurable subConfigurable : ((SearchableConfigurable.Parent)configurable).getConfigurables()) {
        final Configurable config = containsId(id2Select, subConfigurable);
        if (config != null) return config;
      }
    }
    return null;
  }

  @Override
  public void showSettingsDialog(@NotNull final Project project, final Configurable toSelect) {
    getDialog(project, getConfigurableGroups(project, true), toSelect).show();
  }

  @NotNull
  private static ConfigurableGroup[] filterEmptyGroups(@NotNull final ConfigurableGroup[] group) {
    List<ConfigurableGroup> groups = new ArrayList<ConfigurableGroup>();
    for (ConfigurableGroup g : group) {
      if (g.getConfigurables().length > 0) {
        groups.add(g);
      }
    }
    return groups.toArray(new ConfigurableGroup[groups.size()]);
  }

  @Override
  public boolean editConfigurable(Project project, Configurable configurable) {
    return editConfigurable(project, createDimensionKey(configurable), configurable);
  }

  @Override
  public <T extends Configurable> T findApplicationConfigurable(final Class<T> confClass) {
    return ConfigurableExtensionPointUtil.findApplicationConfigurable(confClass);
  }

  @Override
  public <T extends Configurable> T findProjectConfigurable(final Project project, final Class<T> confClass) {
    return ConfigurableExtensionPointUtil.findProjectConfigurable(project, confClass);
  }

  @Override
  public boolean editConfigurable(Project project, String dimensionServiceKey, @NotNull Configurable configurable) {
    return editConfigurable(null, project, configurable, dimensionServiceKey, null);
  }

  @Override
  public boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization) {
    return editConfigurable(null, project, configurable, createDimensionKey(configurable), advancedInitialization);
  }

  @Override
  public boolean editConfigurable(Component parent, Configurable configurable) {
    return editConfigurable(parent, configurable, null);
  }

  @Override
  public boolean editConfigurable(final Component parent, final Configurable configurable, @Nullable final Runnable advancedInitialization) {
    return editConfigurable(parent, null, configurable, createDimensionKey(configurable), advancedInitialization);
  }

  private static boolean editConfigurable(final @Nullable Component parent, @Nullable Project project, final Configurable configurable, final String dimensionKey,
                                          @Nullable final Runnable advancedInitialization) {
    SingleConfigurableEditor editor;
    if (parent != null) {
      editor = new SingleConfigurableEditor(parent, configurable, dimensionKey);
    }
    else {
      editor = new SingleConfigurableEditor(project, configurable, dimensionKey);
    }
    if (advancedInitialization != null) {
      new UiNotifyConnector.Once(editor.getContentPane(), new Activatable.Adapter() {
        @Override
        public void showNotify() {
          advancedInitialization.run();
        }
      });
    }
    editor.show();
    return editor.isOK();
  }

  public static String createDimensionKey(Configurable configurable) {
    String displayName = configurable.getDisplayName();
    displayName = displayName.replaceAll("\n", "_").replaceAll(" ", "_");
    return "#" + displayName;
  }

  @Override
  public boolean editConfigurable(Component parent, String dimensionServiceKey,Configurable configurable) {
    return editConfigurable(parent, null, configurable, dimensionServiceKey, null);
  }

  public boolean isAlreadyShown() {
    return myShown.get();
  }
}
