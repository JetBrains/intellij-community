// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.ListPluginModel;
import com.intellij.ide.plugins.PluginsGroupType;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@ApiStatus.Internal
public class PluginsGroup {
  protected final @Nls String myTitlePrefix;
  public @Nls String title;
  public JLabel titleLabel;
  public LinkLabel<Object> rightAction;
  public List<JComponent> rightActions;
  public UIPluginGroup ui;
  public Runnable clearCallback;
  public PluginsGroupType type;
  private final List<PluginUiModel> models = new ArrayList<>();
  private final ListPluginModel preloadedModel = new ListPluginModel();

  public PluginsGroup(@NotNull @Nls String title, @NotNull PluginsGroupType type) {
    myTitlePrefix = title;
    this.title = title;
    this.type = type;
  }

  public void clear() {
    ui = null;
    models.clear();
    titleLabel = null;
    rightAction = null;
    rightActions = null;
    if (clearCallback != null) {
      clearCallback.run();
      clearCallback = null;
    }
  }

  public void addRightAction(@NotNull JComponent component) {
    if (rightActions == null) {
      rightActions = new ArrayList<>();
    }
    rightActions.add(component);
  }

  public void titleWithCount() {
    title = myTitlePrefix + " (" + models.size() + ")";
    updateTitle();
  }

  public void titleWithEnabled(@NotNull PluginModelFacade pluginModelFacade) {
    int enabled = 0;
    for (PluginUiModel descriptor : models) {
      if (pluginModelFacade.isLoaded(descriptor) &&
          pluginModelFacade.isEnabled(descriptor) &&
          !descriptor.isIncompatible()) {
        enabled++;
      }
    }
    titleWithCount(enabled);
  }

  public void titleWithCount(int enabled) {
    title = IdeBundle.message("plugins.configurable.title.with.count", myTitlePrefix, enabled, models.size());
    updateTitle();
  }

  public int getPluginIndex(@NotNull PluginId pluginId) {
    for (int i = 0; i < models.size(); i++) {
      if (models.get(i).getPluginId().equals(pluginId)) {
        return i;
      }
    }
    return -1;
  }

  public ListPluginModel getPreloadedModel() {
    return preloadedModel;
  }

  protected void updateTitle() {
    if (titleLabel != null) {
      titleLabel.setText(title);
    }
  }

  public int addWithIndex(@NotNull PluginUiModel model) {
    models.add(model);
    sortByName();
    return models.indexOf(model);
  }

  @Deprecated
  public void addDescriptor(@NotNull IdeaPluginDescriptor descriptor) {
    models.add(new PluginUiModelAdapter(descriptor));
  }

  public void addModel(@NotNull PluginUiModel model) {
    models.add(model);
  }

  @Deprecated
  public void addDescriptors(@NotNull Collection<? extends IdeaPluginDescriptor> descriptors) {
    this.models.addAll(ContainerUtil.map(descriptors, PluginUiModelAdapter::new));
  }

  public void addModels(@NotNull Collection<? extends PluginUiModel> models) {
    this.models.addAll(models);
  }

  public void addModels(int index, @NotNull Collection<PluginUiModel> models) {
    this.models.addAll(index, models);
  }

  public void removeDescriptor(@NotNull PluginUiModel model) {
    models.remove(model);
  }

  @Deprecated
  public @NotNull List<IdeaPluginDescriptor> getDescriptors() {
    return ContainerUtil.map(models, it -> it.getDescriptor());
  }

  public @NotNull List<PluginUiModel> getModels() {
    return models;
  }

  public void removeDuplicates(){
    ContainerUtil.removeDuplicates(models);
  }

  public void sortByName() {
    sortByName(models);
  }

  public static void sortByName(@NotNull List<PluginUiModel> models) {
    ContainerUtil.sort(models, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));
  }
}