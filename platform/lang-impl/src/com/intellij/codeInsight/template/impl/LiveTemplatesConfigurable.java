// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts.ConfigurableName;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class LiveTemplatesConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  static final String ID = "editing.templates";
  private TemplateListPanel myPanel;

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public JComponent createComponent() {
    myPanel = new TemplateListPanel();
    return myPanel;
  }

  @Override
  public String getDisplayName() {
    return displayName();
  }

  public static @ConfigurableName @NotNull String displayName() {
    return CodeInsightBundle.message("templates.settings.page.title");
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      Disposer.dispose(myPanel);
    }
    myPanel = null;
  }

  @Override
  public @NotNull String getHelpTopic() {
    return ID;
  }

  @Override
  public @NotNull String getId() {
    return getHelpTopic();
  }

  @Override
  public @Nullable Runnable enableSearch(final String option) {
    return () -> myPanel.selectNode(option);
  }

  public TemplateListPanel getTemplateListPanel() {
    return myPanel;
  }

}
