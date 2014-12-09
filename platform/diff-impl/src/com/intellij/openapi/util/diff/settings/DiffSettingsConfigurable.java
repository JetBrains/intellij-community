package com.intellij.openapi.util.diff.settings;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class DiffSettingsConfigurable implements SearchableConfigurable {
  private DiffSettingsPanel mySettingsPane;

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Nullable
  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Diff";
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "diff.base";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new DiffSettingsPanel();
    }
    return mySettingsPane.getPanel();
  }

  @Override
  public boolean isModified() {
    return mySettingsPane != null && mySettingsPane.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    if (mySettingsPane != null) {
      mySettingsPane.apply();
    }
  }

  @Override
  public void reset() {
    if (mySettingsPane != null) {
      mySettingsPane.reset();
    }
  }

  @Override
  public void disposeUIResources() {
    mySettingsPane = null;
  }
}
