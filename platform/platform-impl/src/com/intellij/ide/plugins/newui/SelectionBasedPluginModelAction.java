// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginEnableDisableAction;
import com.intellij.ide.plugins.PluginEnabledState;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.Producer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static com.intellij.openapi.util.text.StringUtil.split;
import static com.intellij.util.containers.ContainerUtil.*;

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull MyPluginModel myPluginModel;
  protected final boolean myShowShortcut;
  protected final @NotNull List<C> mySelection;
  private final @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> myPluginDescriptor;

  protected SelectionBasedPluginModelAction(@NotNull @Nls String text,
                                            @NotNull MyPluginModel pluginModel,
                                            boolean showShortcut,
                                            @NotNull List<C> selection,
                                            @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
    super(text);

    myPluginModel = pluginModel;
    myShowShortcut = showShortcut;
    mySelection = selection;
    myPluginDescriptor = pluginDescriptor;
  }

  protected final void setShortcutSet(@NotNull ShortcutSet shortcutSet,
                                      boolean show) {
    setShortcutSet(show ? shortcutSet : CustomShortcutSet.EMPTY);
  }

  protected final @Nullable IdeaPluginDescriptor getPluginDescriptor(@NotNull C component) {
    return myPluginDescriptor.apply(component);
  }

  protected final @NotNull Set<? extends IdeaPluginDescriptor> getAllDescriptors() {
    return map2SetNotNull(mySelection, this::getPluginDescriptor);
  }

  static final class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private static final CustomShortcutSet SHORTCUT_SET = new CustomShortcutSet(KeyEvent.VK_SPACE);

    private final @NotNull PluginEnableDisableAction myAction;

    EnableDisableAction(@NotNull MyPluginModel pluginModel,
                        @NotNull PluginEnableDisableAction action,
                        boolean showShortcut,
                        @NotNull List<C> selection,
                        @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(action.toString(),
            pluginModel,
            showShortcut,
            selection,
            pluginDescriptor);

      myAction = action;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Set<? extends IdeaPluginDescriptor> descriptors = getAllDescriptors();
      List<PluginId> pluginIds = mapNotNull(descriptors, IdeaPluginDescriptor::getPluginId);
      List<PluginEnabledState> states = map(pluginIds, myPluginModel::getState);

      boolean allEnabled = all(states, PluginEnabledState.ENABLED::equals);
      boolean isForceEnableAll = myAction == PluginEnableDisableAction.ENABLE_GLOBALLY &&
                                 !allEnabled;

      boolean disabled = pluginIds.isEmpty() ||
                         !all(states, myAction::isApplicable) ||
                         (myAction == PluginEnableDisableAction.DISABLE_GLOBALLY ||
                          myAction == PluginEnableDisableAction.DISABLE_FOR_PROJECT) &&
                         exists(pluginIds, myPluginModel::isRequiredPluginForProject) ||
                         myAction.isPerProject() && (e.getProject() == null ||
                                                     !isPerProjectEnabled() ||
                                                     exists(pluginIds, EnableDisableAction::isPluginExcluded) ||
                                                     exists(descriptors, myPluginModel::requiresRestart));

      boolean enabled = !disabled;
      e.getPresentation().setEnabledAndVisible(isForceEnableAll || enabled);

      boolean isForceDisableAll = myAction == PluginEnableDisableAction.DISABLE_GLOBALLY &&
                                  allEnabled;
      setShortcutSet(SHORTCUT_SET,
                     myShowShortcut && (isForceEnableAll || isForceDisableAll));
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      myPluginModel.changeEnableDisable(
        getAllDescriptors(),
        myAction
      );
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

    private static final ShortcutSet SHORTCUT_SET;

    static {
      ShortcutSet deleteShortcutSet = EventHandler.getShortcuts(IdeActions.ACTION_EDITOR_DELETE);
      SHORTCUT_SET = deleteShortcutSet != null ?
                     deleteShortcutSet :
                     new CustomShortcutSet(EventHandler.DELETE_CODE);
    }

    private final @NotNull JComponent myUiParent;

    UninstallAction(@NotNull MyPluginModel pluginModel,
                    boolean showShortcut,
                    @NotNull JComponent uiParent,
                    @NotNull List<C> selection,
                    @NotNull Function<@NotNull ? super C, @Nullable ? extends IdeaPluginDescriptor> pluginDescriptor) {
      super(IdeBundle.message("plugins.configurable.uninstall.button"),
            pluginModel,
            showShortcut,
            selection,
            pluginDescriptor);

      myUiParent = uiParent;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      Set<? extends IdeaPluginDescriptor> descriptors = getAllDescriptors();

      boolean disabled = descriptors.isEmpty() ||
                         exists(descriptors, IdeaPluginDescriptor::isBundled) ||
                         exists(descriptors, myPluginModel::isUninstalled);
      e.getPresentation().setEnabledAndVisible(!disabled);

      setShortcutSet(SHORTCUT_SET, myShowShortcut);
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
                                                  @NotNull Function<? super @NotNull PluginEnableDisableAction, @NotNull EnableDisableAction<C>> createEnableDisableAction,
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