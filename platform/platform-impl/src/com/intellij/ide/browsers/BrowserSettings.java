// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.browsers;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class BrowserSettings implements SearchableConfigurable, Configurable.NoScroll {
  private BrowserSettingsPanel myPanel;

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public @Nls String getDisplayName() {
    return IdeBundle.message("browsers.settings");
  }

  @Override
  public @NotNull String getHelpTopic() {
    return "reference.settings.ide.settings.web.browsers";
  }

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new BrowserSettingsPanel();
    }
    return myPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (myPanel != null) {
      myPanel.apply();
    }
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

  public void selectBrowser(@NotNull WebBrowser browser) {
    createComponent();
    myPanel.selectBrowser(browser);
  }
}
