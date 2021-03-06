// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.plugins;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;

@State(
  name = "PluginManagerConfigurable",
  storages = @Storage("plugin_ui.xml")
)
public final class PluginManagerUISettings implements PersistentStateComponent<PluginManagerUISettings>, PerformInBackgroundOption {
  public boolean UPDATE_IN_BACKGROUND;

  public static PluginManagerUISettings getInstance() {
    return ApplicationManager.getApplication().getService(PluginManagerUISettings.class);
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
