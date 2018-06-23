// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.ui.customization;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CustomizationConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private CustomizableActionsPanel myPanel;

  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new CustomizableActionsPanel();
    }
    return myPanel.getPanel();
  }

  public String getDisplayName() {
    return IdeBundle.message("title.customizations");
  }

  @Override
  public String getHelpTopic() {
    return "preferences.customizations";
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.reset();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  @NotNull
  public String getId() {
    return getHelpTopic();
  }
}
