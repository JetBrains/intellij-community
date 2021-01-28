// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginEnableDisableAction;
import com.intellij.ide.plugins.PluginEnabledState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ShortcutSet;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Producer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.split;
import static com.intellij.util.containers.ContainerUtil.*;

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull MyPluginModel myPluginModel;
  protected final @NotNull List<C> mySelection;
  private final @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> myPluginDescriptor;

  protected SelectionBasedPluginModelAction(@NotNull @Nls String text,
                                            @Nullable ShortcutSet shortcutSet,
                                            @NotNull MyPluginModel pluginModel,
                                            @NotNull List<C> selection,
                                            @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
    super(text);
    setShortcutSet(shortcutSet == null ? CustomShortcutSet.EMPTY : shortcutSet);

    myPluginModel = pluginModel;
    mySelection = selection;
    myPluginDescriptor = pluginDescriptor;
  }

  protected final @Nullable IdeaPluginDescriptor getPluginDescriptor(@NotNull C component) {
    return myPluginDescriptor.apply(component);
  }

  protected final @NotNull Set<? extends IdeaPluginDescriptor> getAllDescriptors() {
    return map2SetNotNull(mySelection, this::getPluginDescriptor);
  }

  static final class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private final @NotNull PluginEnableDisableAction myAction;

    EnableDisableAction(@Nullable ShortcutSet shortcutSet,
                        @NotNull MyPluginModel pluginModel,
                        @NotNull PluginEnableDisableAction action,
                        @NotNull List<C> selection,
                        @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(
        action.toString(),
        isCheckboxesEnabled() ? shortcutSet : null,
        pluginModel,
        selection,
        pluginDescriptor
      );

      myAction = action;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Set<? extends IdeaPluginDescriptor> descriptors = getAllDescriptors();
      List<PluginId> pluginIds = mapNotNull(descriptors, IdeaPluginDescriptor::getPluginId);
      List<PluginEnabledState> states = map(pluginIds, myPluginModel::getState);

      boolean isForceEnableAll = myAction == PluginEnableDisableAction.ENABLE_GLOBALLY &&
                                 !all(states, PluginEnabledState.ENABLED::equals);
      boolean disabled = pluginIds.isEmpty() ||
                         !all(states, myAction::isApplicable) ||
                         myAction.isDisable() && exists(pluginIds, myPluginModel::isRequiredPluginForProject) ||
                         myAction.isPerProject() && (e.getProject() == null ||
                                                     !isPerProjectEnabled() ||
                                                     exists(pluginIds, EnableDisableAction::isPluginExcluded) ||
                                                     exists(descriptors, myPluginModel::requiresRestart));

      e.getPresentation().setEnabledAndVisible(isForceEnableAll || !disabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPluginModel.changeEnableDisable(
        getAllDescriptors(),
        myAction
      );
    }

    private static boolean isCheckboxesEnabled() {
      return Registry.is("ide.plugins.per.project.use.checkboxes", false);
    }

    private static boolean isPerProjectEnabled() {
      return Registry.is("ide.plugins.per.project", false);
    }

    private static boolean isPluginExcluded(@NotNull PluginId pluginId) {
      return split(Registry.stringValue("ide.plugins.per.project.exclusion.list"), ",")
        .contains(pluginId.getIdString());
    }
  }

  static final class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private final @NotNull JComponent myUiParent;

    UninstallAction(@Nullable ShortcutSet shortcutSet,
                    @NotNull MyPluginModel pluginModel,
                    @NotNull JComponent uiParent,
                    @NotNull List<C> selection,
                    @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(
        IdeBundle.message("plugins.configurable.uninstall.button"),
        shortcutSet,
        pluginModel,
        selection,
        pluginDescriptor
      );

      myUiParent = uiParent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Set<? extends IdeaPluginDescriptor> descriptors = getAllDescriptors();

      boolean disabled = descriptors.isEmpty() ||
                         exists(descriptors, IdeaPluginDescriptor::isBundled) ||
                         exists(descriptors, myPluginModel::isUninstalled);
      e.getPresentation().setEnabledAndVisible(!disabled);
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
                                                  @NotNull Function<@NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                  @NotNull Producer<@NotNull UninstallAction<C>> createUninstallAction) {
    PluginEnableDisableAction[] actions = PluginEnableDisableAction.values();
    for (int i = 0; i < actions.length; i++) {
      group.add(createEnableDisableAction.apply(actions[i]));
      if ((i + 1) % 3 == 0) {
        group.addSeparator();
      }
    }
    group.add(createUninstallAction.produce());
  }

  static <C extends JComponent> @NotNull JComponent createGearButton(@NotNull Function<@NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
                                                                     @NotNull Producer<@NotNull UninstallAction<C>> createUninstallAction) {
    DefaultActionGroup result = new DefaultActionGroup();
    addActionsTo(result,
                 createEnableDisableAction,
                 createUninstallAction);

    return TabbedPaneHeaderComponent.createToolbar(result,
                                                   IdeBundle.message("plugin.settings.link.title"),
                                                   AllIcons.General.GearHover);
  }
}