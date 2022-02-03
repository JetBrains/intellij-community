// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.TabbedConfigurable;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.options.newEditor.SettingsDialogFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.navigation.Place;
import com.intellij.util.ui.update.Activatable;
import com.intellij.util.ui.update.UiNotifyConnector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import static com.intellij.openapi.project.ProjectUtil.currentOrDefaultProject;

// extended externally
public class ShowSettingsUtilImpl extends ShowSettingsUtil {
  private static final Logger LOG = Logger.getInstance(ShowSettingsUtilImpl.class);

  public static @NotNull DialogWrapper getDialog(@Nullable Project project,
                                                 @NotNull List<? extends ConfigurableGroup> groups,
                                                 @Nullable Configurable toSelect) {
    return SettingsDialogFactory.getInstance().create(
      currentOrDefaultProject(project),
      filterEmptyGroups(groups),
      toSelect,
      null
    );
  }

  /**
   * @param project         a project used to load project settings or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return an array with the root configurable group
   */
  public static ConfigurableGroup @NotNull [] getConfigurableGroups(@Nullable Project project, boolean withIdeSettings) {
    ConfigurableGroup group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, withIdeSettings);
    return new ConfigurableGroup[]{group};
  }

  /**
   * @param project         a project used to load project settings or {@code null}
   * @param withIdeSettings specifies whether to load application settings or not
   * @return all configurables as a plain list except the root configurable group
   */
  public static @NotNull List<Configurable> getConfigurables(@Nullable Project project, boolean withIdeSettings) {
    List<Configurable> list = new ArrayList<>();

    for (Configurable configurable : ConfigurableExtensionPointUtil.getConfigurables(
      withIdeSettings ? project : currentOrDefaultProject(project),
      withIdeSettings
    )) {
      list.add(configurable);
      if (configurable instanceof Configurable.Composite) {
        collect(list, ((Configurable.Composite)configurable).getConfigurables());
      }
    }

    return list;
  }

  private static void collect(@NotNull List<? super Configurable> list, Configurable @NotNull [] configurables) {
    for (Configurable configurable : configurables) {
      list.add(configurable);
      if (configurable instanceof Configurable.Composite) {
        collect(list, ((Configurable.Composite)configurable).getConfigurables());
      }
    }
  }

  @Override
  public void showSettingsDialog(@NotNull Project project, ConfigurableGroup @NotNull ... groups) {
    try {
      getDialog(project, Arrays.asList(groups), null).show();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  @Override
  public <T extends Configurable> void showSettingsDialog(@Nullable Project project, @NotNull Class<T> configurableClass) {
    showSettingsDialog(project, configurableClass, null);
  }

  @Override
  public <T extends Configurable> void showSettingsDialog(@Nullable Project project,
                                                          @NotNull Class<T> configurableClass,
                                                          @Nullable Consumer<? super T> additionalConfiguration) {
    assert Configurable.class.isAssignableFrom(configurableClass) : "Not a configurable: " + configurableClass.getName();
    showSettingsDialog(project, it -> ConfigurableWrapper.cast(configurableClass, it) != null, it -> {
      if (additionalConfiguration != null) {
        T toConfigure = ConfigurableWrapper.cast(configurableClass, it);
        assert toConfigure != null : "Wrong configurable found: " + it.getClass();
        additionalConfiguration.accept(toConfigure);
      }
    });
  }

  @Override
  public void showSettingsDialog(@Nullable Project project,
                                 @NotNull Predicate<? super Configurable> predicate,
                                 @Nullable Consumer<? super Configurable> additionalConfiguration) {
    ConfigurableGroup[] groups = getConfigurableGroups(project, true);
    Configurable config = new ConfigurableVisitor() {
      @Override
      protected boolean accept(@NotNull Configurable configurable) {
        return predicate.test(configurable);
      }
    }.find(groups);

    assert config != null : "Cannot find configurable for specified predicate";

    if (additionalConfiguration != null) {
      additionalConfiguration.accept(config);
    }

    getDialog(project, Arrays.asList(groups), config).show();
  }

  @Override
  public void showSettingsDialog(@Nullable Project project, @NotNull String nameToSelect) {
    ConfigurableGroup group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */ true);
    List<ConfigurableGroup> groups = group.getConfigurables().length == 0 ? Collections.emptyList() : Collections.singletonList(group);
    getDialog(project, groups, findPreselectedByDisplayName(nameToSelect, groups)).show();
  }

  @Nullable
  private static Configurable findPreselectedByDisplayName(@NotNull String preselectedConfigurableDisplayName, @NotNull List<? extends ConfigurableGroup> groups) {
    for (ConfigurableGroup eachGroup : groups) {
      for (Configurable configurable : SearchUtil.expandGroup(eachGroup)) {
        if (preselectedConfigurableDisplayName.equals(configurable.getDisplayName())) {
          return configurable;
        }
      }
    }
    return null;
  }

  public static void showSettingsDialog(@Nullable Project project, @Nullable String idToSelect, final String filter) {
    ConfigurableGroup group = ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */ true);
    if (group.getConfigurables().length == 0) {
      group = null;
    }

    Configurable configurableToSelect = idToSelect == null ? null : ConfigurableVisitor.findById(idToSelect, Collections.singletonList(group));
    SettingsDialogFactory.getInstance().create(
      currentOrDefaultProject(project),
      Collections.singletonList(group),
      configurableToSelect,
      filter
    ).show();
  }

  @Override
  public void showSettingsDialog(@NotNull Project project, @Nullable Configurable toSelect) {
    List<ConfigurableGroup> groups = Collections.singletonList(ConfigurableExtensionPointUtil.getConfigurableGroup(project, /* withIdeSettings = */ true));
    getDialog(project, groups, toSelect).show();
  }

  @NotNull
  private static List<ConfigurableGroup> filterEmptyGroups(@NotNull List<? extends ConfigurableGroup> group) {
    List<ConfigurableGroup> groups = new ArrayList<>();
    for (ConfigurableGroup g : group) {
      if (g.getConfigurables().length > 0) {
        groups.add(g);
      }
    }
    return groups;
  }

  @Override
  public boolean editConfigurable(Project project, @NotNull Configurable configurable) {
    return editConfigurable(project, createDimensionKey(configurable), configurable);
  }

  @Override
  public boolean editConfigurable(Project project, @NotNull String dimensionServiceKey, @NotNull Configurable configurable) {
    return editConfigurable(project, dimensionServiceKey, configurable, isWorthToShowApplyButton(configurable));
  }

  private static boolean isWorthToShowApplyButton(@NotNull Configurable configurable) {
    return configurable instanceof Place.Navigator ||
           configurable instanceof Composite ||
           configurable instanceof TabbedConfigurable;
  }

  @Override
  public boolean editConfigurable(Project project, @NotNull String dimensionServiceKey, @NotNull Configurable configurable, boolean showApplyButton) {
    return editConfigurable(null, project, configurable, dimensionServiceKey, null, showApplyButton);
  }

  @Override
  public boolean editConfigurable(Project project, @NotNull Configurable configurable, Runnable advancedInitialization) {
    return editConfigurable(null, project, configurable, createDimensionKey(configurable), advancedInitialization, isWorthToShowApplyButton(configurable));
  }

  @Override
  public <T extends Configurable> boolean editConfigurable(Project project, @NotNull T configurable, @NotNull Consumer<T> advancedInitialization) {
    return editConfigurable(null, project, configurable, advancedInitialization, createDimensionKey(configurable), isWorthToShowApplyButton(configurable));
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable) {
    return editConfigurable(parent, configurable, null);
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull String displayName) {
    return editConfigurable(parent, displayName, (Runnable)null);
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull String displayName, @Nullable Runnable advancedInitialization) {
    ConfigurableGroup group = ConfigurableExtensionPointUtil.getConfigurableGroup(null, /* withIdeSettings = */ true);
    List<ConfigurableGroup> groups = group.getConfigurables().length == 0 ? Collections.emptyList() : Collections.singletonList(group);
    Configurable configurable = findPreselectedByDisplayName(displayName, groups);
    if (configurable == null) {
      LOG.error("Cannot find configurable for name [" + displayName + "]");
      return false;
    }
    return editConfigurable(parent, configurable, advancedInitialization);
  }

  @Override
  public boolean editConfigurable(@Nullable Component parent, @NotNull Configurable configurable, @Nullable Runnable advancedInitialization) {
    return editConfigurable(parent, null, configurable, createDimensionKey(configurable), advancedInitialization, isWorthToShowApplyButton(configurable));
  }

  private static boolean editConfigurable(@Nullable Component parent,
                                          @Nullable Project project,
                                          @NotNull Configurable configurable,
                                          @NotNull String dimensionKey,
                                          @Nullable Runnable advancedInitialization,
                                          boolean showApplyButton) {
    Consumer<Configurable> consumer = advancedInitialization != null ? it -> advancedInitialization.run() : null;
    return editConfigurable(parent, project, configurable, consumer, dimensionKey, showApplyButton);
  }

  private static <T extends Configurable> boolean editConfigurable(@Nullable Component parent,
                                                                   @Nullable Project project,
                                                                   @NotNull T configurable,
                                                                   @Nullable final Consumer<T> advancedInitialization,
                                                                   @NotNull String dimensionKey,
                                                                   boolean showApplyButton) {
    final DialogWrapper editor;
    if (parent == null) {
      editor = SettingsDialogFactory.getInstance().create(project, dimensionKey, configurable, showApplyButton, false);
    }
    else {
      editor = SettingsDialogFactory.getInstance().create(parent, dimensionKey, configurable, showApplyButton, false);
    }
    if (advancedInitialization != null) {
      new UiNotifyConnector.Once(editor.getContentPane(), new Activatable() {
        @Override
        public void showNotify() {
          advancedInitialization.accept(configurable);
        }
      });
    }
    return editor.showAndGet();
  }

  @NotNull
  public static String createDimensionKey(@NotNull Configurable configurable) {
    return '#' + configurable.getDisplayName().replace('\n', '_').replace(' ', '_');
  }

  @Override
  public boolean editConfigurable(Component parent, @NotNull String dimensionServiceKey, @NotNull Configurable configurable) {
    return editConfigurable(parent, null, configurable, dimensionServiceKey, null, isWorthToShowApplyButton(configurable));
  }
}
