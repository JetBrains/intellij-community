// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins.newui;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class PluginsGroup {
  protected final @Nls String myTitlePrefix;
  public @Nls String title;
  public JLabel titleLabel;
  public LinkLabel<Object> rightAction;
  public List<JComponent> rightActions;
  public final List<IdeaPluginDescriptor> descriptors = new ArrayList<>();
  public UIPluginGroup ui;
  public Runnable clearCallback;

  public PluginsGroup(@NotNull @Nls String title) {
    myTitlePrefix = title;
    this.title = title;
  }

  public void clear() {
    ui = null;
    descriptors.clear();
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
    title = myTitlePrefix + " (" + descriptors.size() + ")";
    updateTitle();
  }

  public void titleWithEnabled(@NotNull MyPluginModel pluginModel) {
    int enabled = 0;
    for (IdeaPluginDescriptor descriptor : descriptors) {
      if (pluginModel.isLoaded(descriptor.getPluginId()) &&
          pluginModel.isEnabled(descriptor) &&
          !PluginManagerCore.isIncompatible(descriptor)) {
        enabled++;
      }
    }
    titleWithCount(enabled);
  }

  public void titleWithCount(int enabled) {
    title = IdeBundle.message("plugins.configurable.title.with.count", myTitlePrefix, enabled, descriptors.size());
    updateTitle();
  }

  protected void updateTitle() {
    if (titleLabel != null) {
      titleLabel.setText(title);
    }
  }

  public int addWithIndex(@NotNull IdeaPluginDescriptor descriptor) {
    descriptors.add(descriptor);
    sortByName();
    return descriptors.indexOf(descriptor);
  }

  public void sortByName() {
    sortByName(descriptors);
  }

  public static void sortByName(@NotNull List<? extends IdeaPluginDescriptor> descriptors) {
    ContainerUtil.sort(descriptors, (o1, o2) -> StringUtil.compare(o1.getName(), o2.getName(), true));
  }
}