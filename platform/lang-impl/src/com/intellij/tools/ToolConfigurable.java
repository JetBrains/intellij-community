// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.tools;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class ToolConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private BaseToolsPanel myPanel;

  @Override
  public String getDisplayName() {
    return ToolsBundle.message("tools.settings.title");
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new ToolsPanel();
    }
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public void reset() {
    if (myPanel != null) {
      myPanel.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public String getHelpTopic() {
    return "preferences.externalTools";
  }


  @Override
  public @NotNull String getId() {
    return "preferences.externalTools";
  }
}
