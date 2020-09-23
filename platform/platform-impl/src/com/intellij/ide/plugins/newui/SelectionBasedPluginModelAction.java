// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginEnabledState;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

import javax.swing.*;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

abstract class SelectionBasedPluginModelAction<C extends JComponent> extends DumbAwareAction {

  protected final @NotNull MyPluginModel myPluginModel;
  protected final @NotNull List<C> mySelection;

  protected SelectionBasedPluginModelAction(@NotNull @PropertyKey(resourceBundle = IdeBundle.BUNDLE) String propertyKey,
                                            @NotNull MyPluginModel pluginModel,
                                            @NotNull List<C> selection) {
    super(IdeBundle.message(propertyKey));
    myPluginModel = pluginModel;
    mySelection = selection;
  }

  protected abstract @Nullable IdeaPluginDescriptor getPluginDescriptor(@NotNull C component);

  static abstract class EnableDisableAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    protected final @NotNull PluginEnabledState myNewState;

    protected EnableDisableAction(@NotNull MyPluginModel pluginModel,
                                  @NotNull List<C> selection,
                                  @NotNull PluginEnabledState newState) {
      super(
        getActionTextPropertyKey(newState),
        pluginModel,
        selection
      );

      myNewState = newState;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Set<IdeaPluginDescriptor> plugins = mySelection.stream()
        .map(this::getPluginDescriptor)
        .filter(Objects::nonNull)
        .collect(Collectors.toSet());

      myPluginModel.changeEnableDisable(
        plugins,
        myNewState
      );
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
  }

  static abstract class UninstallAction<C extends JComponent> extends SelectionBasedPluginModelAction<C> {

    private final @NotNull JComponent myUiParent;

    protected UninstallAction(@NotNull MyPluginModel pluginModel,
                              @NotNull JComponent uiParent,
                              @NotNull List<C> selection) {
      super(
        "plugins.configurable.uninstall.button",
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
      boolean enabled = mySelection.stream()
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
}