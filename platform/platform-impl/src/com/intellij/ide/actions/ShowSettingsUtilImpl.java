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

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.options.ex.*;
import com.intellij.openapi.options.newEditor.SettingsDialog;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
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

  @NotNull
  private static Project getProject(@Nullable Project project) {
    return project != null ? project : ProjectManager.getInstance().getDefaultProject();
  }

  @NotNull
  public static DialogWrapper getDialog(@Nullable Project project, @NotNull ConfigurableGroup[] groups, @Nullable Configurable toSelect) {
    project = getProject(project);
    final ConfigurableGroup[] filteredGroups = filterEmptyGroups(groups);
    return new SettingsDialog(project, filteredGroups, toSelect, null);
  }

  @NotNull
  public static ConfigurableGroup[] getConfigurableGroups(@Nullable Project project, boolean withIdeSettings) {
    if (!withIdeSettings) {
      project = getProject(project);
    }
    return new ConfigurableGroup[]{ConfigurableExtensionPointUtil.getConfigurableGroup(project, withIdeSettings)};
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
      getDialog(project, group, null).show();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public void showSettingsDialog(@Nullable final Project project, final Class configurableClass) {
    assert Configurable.class.isAssignableFrom(configurableClass) : "Not a configurable: " + configurableClass.getName();

    ConfigurableGroup[] groups = getConfigurableGroups(project, true);

    Configurable config = new ConfigurableVisitor.ByType(configurableClass).find(groups);

    assert config != null : "Cannot find configurable: " + configurableClass.getName();

    getDialog(project, groups, config).show();
  }

  @Override
  public void showSettingsDialog(@Nullable final Project project, @NotNull final String nameToSelect) {
    ConfigurableGroup[] groups = getConfigurableGroups(project, true);
    Project actualProject = getProject(project);

    groups = filterEmptyGroups(groups);
    getDialog(actualProject, groups, findPreselectedByDisplayName(nameToSelect, groups)).show();
  }

  @Nullable
  private static Configurable findPreselectedByDisplayName(final String preselectedConfigurableDisplayName, ConfigurableGroup[] groups) {
    final List<Configurable> all = SearchUtil.expand(groups);
    for (Configurable each : all) {
      if (preselectedConfigurableDisplayName.equals(each.getDisplayName())) return each;
    }
    return null;
  }

  public static void showSettingsDialog(@Nullable Project project, final String id2Select, final String filter) {
    ConfigurableGroup[] group = getConfigurableGroups(project, true);

    group = filterEmptyGroups(group);
    final Configurable configurable2Select = id2Select == null ? null : new ConfigurableVisitor.ByID(id2Select).find(group);

    new SettingsDialog(getProject(project), group, configurable2Select, filter).show();
  }

  @Override
  public void showSettingsDialog(@NotNull final Project project, final Configurable toSelect) {
    getDialog(project, getConfigurableGroups(project, true), toSelect).show();
  }

  @NotNull
  private static ConfigurableGroup[] filterEmptyGroups(@NotNull final ConfigurableGroup[] group) {
    List<ConfigurableGroup> groups = new ArrayList<>();
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
    //noinspection deprecation
    return ConfigurableExtensionPointUtil.findProjectConfigurable(project, confClass);
  }

  @Override
  public boolean editConfigurable(Project project, String dimensionServiceKey, @NotNull Configurable configurable) {
    return editConfigurable(project, dimensionServiceKey, configurable, isWorthToShowApplyButton(configurable));
  }

  private static boolean isWorthToShowApplyButton(@NotNull Configurable configurable) {
    return configurable instanceof Place.Navigator ||
           configurable instanceof Composite ||
           configurable instanceof TabbedConfigurable;
  }

  @Override
  public boolean editConfigurable(Project project, String dimensionServiceKey, @NotNull Configurable configurable, boolean showApplyButton) {
    return editConfigurable(null, project, configurable, dimensionServiceKey, null, showApplyButton);
  }

  @Override
  public boolean editConfigurable(Project project, Configurable configurable, Runnable advancedInitialization) {
    return editConfigurable(null, project, configurable, createDimensionKey(configurable), advancedInitialization, isWorthToShowApplyButton(configurable));
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable) {
    return editConfigurable(parent, configurable, null);
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable, @Nullable Runnable advancedInitialization) {
    return editConfigurable(parent, null, configurable, createDimensionKey(configurable), advancedInitialization, isWorthToShowApplyButton(configurable));
  }

  private static boolean editConfigurable(@Nullable Component parent,
                                          @Nullable Project project,
                                          @NotNull Configurable configurable,
                                          String dimensionKey,
                                          @Nullable final Runnable advancedInitialization,
                                          boolean showApplyButton) {
    final DialogWrapper editor;
    if (parent == null) {
      editor = new SettingsDialog(project, dimensionKey, configurable, showApplyButton, false);
    }
    else {
      editor = new SettingsDialog(parent, dimensionKey, configurable, showApplyButton, false);
    }
    if (advancedInitialization != null) {
      new UiNotifyConnector.Once(editor.getContentPane(), new Activatable.Adapter() {
        @Override
        public void showNotify() {
          advancedInitialization.run();
        }
      });
    }
    return editor.showAndGet();
  }

  @NotNull
  public static String createDimensionKey(@NotNull Configurable configurable) {
    return '#' + StringUtil.replaceChar(StringUtil.replaceChar(configurable.getDisplayName(), '\n', '_'), ' ', '_');
  }

  @Override
  public boolean editConfigurable(Component parent, String dimensionServiceKey, Configurable configurable) {
    return editConfigurable(parent, null, configurable, dimensionServiceKey, null, isWorthToShowApplyButton(configurable));
  }
}
