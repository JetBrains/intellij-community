// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginEnabledState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Producer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull MyPluginModel myPluginModel;
  protected final @NotNull List<C> mySelection;

  protected SelectionBasedPluginModelAction(@NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String propertyKey,
                                            @Nullable ShortcutSet shortcutSet,
                                            @NotNull MyPluginModel pluginModel,
                                            @NotNull List<C> selection) {
    super(IdeBundle.message(propertyKey));
    setShortcutSet(shortcutSet == null ? CustomShortcutSet.EMPTY : shortcutSet);

    myPluginModel = pluginModel;
    mySelection = selection;
  }

  protected abstract @Nullable IdeaPluginDescriptor getPluginDescriptor(@NotNull C component);

  static abstract class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    protected final @NotNull PluginEnabledState myNewState;

    protected EnableDisableAction(@Nullable ShortcutSet shortcutSet,
                                  @NotNull MyPluginModel pluginModel,
                                  @NotNull PluginEnabledState newState,
                                  @NotNull List<C> selection) {
      super(
        getActionTextPropertyKey(newState),
        shortcutSet,
        pluginModel,
        selection
      );

      myNewState = newState;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Presentation presentation = e.getPresentation();
      Project project = e.getProject();

      getAllDescriptors()
        .map(myPluginModel::getState)
        .forEach(state -> update(state, presentation, project));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPluginModel.changeEnableDisable(
        getAllDescriptors().collect(Collectors.toSet()),
        myNewState
      );
    }

    protected boolean isInvisible(@NotNull PluginEnabledState oldState,
                                  @Nullable Project project) {
      return myNewState == oldState ||
             oldState == PluginEnabledState.DISABLED && myNewState == PluginEnabledState.DISABLED_FOR_PROJECT ||
             myNewState.isPerProject() && (!isPerProjectEnabled() || project == null);
    }

    private void update(@NotNull PluginEnabledState oldState,
                        @NotNull Presentation presentation,
                        @Nullable Project project) {
      boolean enabled = !isInvisible(oldState, project);
      presentation.setEnabledAndVisible(enabled);

      if (oldState == PluginEnabledState.ENABLED && myNewState == PluginEnabledState.ENABLED_FOR_PROJECT) {
        presentation.setText(IdeBundle.message("plugins.configurable.enable.for.current.project.only"));
      }
    }

    private @NotNull Stream<IdeaPluginDescriptor> getAllDescriptors() {
      return mySelection
        .stream()
        .map(this::getPluginDescriptor)
        .filter(Objects::nonNull);
    }

    private static @NotNull @NonNls String getActionTextPropertyKey(@NotNull PluginEnabledState newState) {
      switch (newState) {
        case ENABLED_FOR_PROJECT:
          return "plugins.configurable.enable.for.current.project";
        case ENABLED:
          return "plugins.configurable.enable.for.all.projects";
        case DISABLED_FOR_PROJECT:
          return "plugins.configurable.disable.for.current.project";
        case DISABLED:
          return "plugins.configurable.disable.for.all.projects";
        default:
          throw new IllegalArgumentException();
      }
    }

    private static boolean isPerProjectEnabled() {
      return Registry.is("ide.plugins.per.project", false);
    }
  }

  static abstract class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private final @NotNull JComponent myUiParent;

    protected UninstallAction(@Nullable ShortcutSet shortcutSet,
                              @NotNull MyPluginModel pluginModel,
                              @NotNull JComponent uiParent,
                              @NotNull List<C> selection) {
      super(
        "plugins.configurable.uninstall.button",
        shortcutSet,
        pluginModel,
        selection
      );
      myUiParent = uiParent;
    }

    protected boolean isBundled(@NotNull C component) {
      IdeaPluginDescriptor descriptor = getPluginDescriptor(component);
      return descriptor == null ||
             descriptor.isBundled();
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      boolean enabled = !mySelection.isEmpty() &&
                        mySelection
                          .stream()
                          .noneMatch(this::isBundled);
      e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      int size = mySelection.size();
      IdeaPluginDescriptor firstDescriptor = size == 1 ?
                                             getPluginDescriptor(mySelection.get(0)) :
                                             null;
      String name = firstDescriptor != null ?
                    firstDescriptor.getName() :
                    null;

      if (MyPluginModel.showUninstallDialog(myUiParent, name, size)) {
        for (C component : mySelection) {
          IdeaPluginDescriptor descriptor = getPluginDescriptor(component);

          if (descriptor != null) {
            myPluginModel.uninstallAndUpdateUi(component, descriptor);
          }
        }
      }
    }
  }

  static <C extends JComponent> void addActionsTo(@NotNull DefaultActionGroup group,
                                                  @NotNull Function<@NotNull PluginEnabledState, @NotNull ? extends EnableDisableAction<C>> createEnableDisableAction,
                                                  @NotNull Producer<@NotNull ? extends UninstallAction<C>> createUninstallAction) {
    PluginEnabledState[] states = PluginEnabledState.values();
    for (int i = 0; i < states.length; i++) {
      group.add(createEnableDisableAction.apply(states[i]));
      if ((i + 1) % 2 == 0) {
        group.addSeparator();
      }
    }
    group.add(createUninstallAction.produce());
  }

  static <C extends JComponent> @NotNull JComponent createGearButton(@NotNull Function<@NotNull PluginEnabledState, @NotNull ? extends EnableDisableAction<C>> createEnableDisableAction,
                                                                     @NotNull Producer<@NotNull ? extends UninstallAction<C>> createUninstallAction) {
    DefaultActionGroup result = new DefaultActionGroup();
    addActionsTo(
      result,
      createEnableDisableAction,
      createUninstallAction
    );

    return TabbedPaneHeaderComponent.createToolbar(
      IdeBundle.message("plugin.settings.link.title"),
      result
    );
  }
}