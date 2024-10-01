// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

@ApiStatus.Internal
public final class CustomizationConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private CustomizableActionsPanel myPanel;

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new CustomizableActionsPanel();
    }
    return myPanel.getPanel();
  }

  @Override
  public String getDisplayName() {
    return IdeBundle.message("title.customizations");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.customizations";
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }
}
