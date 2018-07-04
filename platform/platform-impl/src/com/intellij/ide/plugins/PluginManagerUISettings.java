// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.ide.ui.SplitterProportionsDataImpl;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@State(
  name = "PluginManagerConfigurable",
  storages = @Storage("plugin_ui.xml")
)
public class PluginManagerUISettings implements PersistentStateComponent<PluginManagerUISettings>, PerformInBackgroundOption {
  public int AVAILABLE_SORT_COLUMN_ORDER = SortOrder.ASCENDING.ordinal();

  public boolean availableSortByStatus;
  public boolean installedSortByStatus;

  public boolean UPDATE_IN_BACKGROUND;

  @Attribute(converter = SplitterProportionsDataImpl.SplitterProportionsConverter.class)
  public SplitterProportionsDataImpl installedProportions = new SplitterProportionsDataImpl();
  @Attribute(converter = SplitterProportionsDataImpl.SplitterProportionsConverter.class)
  public SplitterProportionsDataImpl availableProportions = new SplitterProportionsDataImpl();

  public PluginManagerUISettings() {
    Float defaultProportion = new Float(0.5);
    installedProportions.getProportions().add(defaultProportion);
    availableProportions.getProportions().add(defaultProportion);
  }

  public static PluginManagerUISettings getInstance() {
    return ServiceManager.getService(PluginManagerUISettings.class);
  }

  @Override
  public PluginManagerUISettings getState() {
    return this;
  }

  @Override
  public void loadState(@NotNull PluginManagerUISettings state) {
    XmlSerializerUtil.copyBean(state, this);
  }

  @Override
  public boolean shouldStartInBackground() {
    return UPDATE_IN_BACKGROUND;
  }

  @Override
  public void processSentToBackground() {
    UPDATE_IN_BACKGROUND = true;
  }
}
