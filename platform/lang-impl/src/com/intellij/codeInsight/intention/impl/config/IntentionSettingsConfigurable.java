// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class IntentionSettingsConfigurable implements SearchableConfigurable, MasterDetails, IntentionsConfigurable {
  private IntentionSettingsPanel myPanel;
  public static final @NonNls String HELP_ID = "preferences.intentionPowerPack";

  @Override
  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new IntentionSettingsPanel();
    }
    JPanel component = myPanel.getComponent();
    component.setPreferredSize(JBUI.size(800, 600));
    component.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    return component;
  }

  @Override
  public void initUi() {
    if (myPanel == null) {
      myPanel = new IntentionSettingsPanel();
    }
    myPanel.initUi();
  }

  @Override
  public JComponent getToolbar() {
    return myPanel.getToolbar();
  }

  @Override
  public JComponent getMaster() {
    return myPanel.getMaster();
  }

  @Override
  public DetailsComponent getDetails() {
    return myPanel.getDetails();
  }

  @Override
  public @Nullable JComponent getPreferredFocusedComponent() {
    return myPanel == null ? null : myPanel.getIntentionTree();
  }

  @Override
  public boolean isModified() {
    return myPanel != null && myPanel.isModified();
  }

  @Override
  public String getDisplayName() {
    return getDisplayNameText();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void apply() {
    myPanel.apply();
  }

  @Override
  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
    myPanel = null;
  }

  @Override
  public String getHelpTopic() {
    return HELP_ID;
  }

  @Override
  public Runnable enableSearch(String option) {
    return myPanel.showOption(option);
  }

  @Override
  public @NotNull String getId() {
    return HELP_ID;
  }

  @Override
  public void selectIntention(@NotNull String familyName) {
    if (myPanel != null) {
      myPanel.selectIntention(familyName);
    }
  }

  public static @NlsContexts.ConfigurableName String getDisplayNameText() {
    return CodeInsightBundle.message("intention.settings");
  }
}
