package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.MasterDetails;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.ui.DetailsComponent;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class IntentionSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable, MasterDetails {
  private IntentionSettingsPanel myPanel;
  @NonNls public static final String HELP_ID = "preferences.intentionPowerPack";
  public static final String DISPLAY_NAME = CodeInsightBundle.message("intention.settings");

  public JComponent createComponent() {
    if (myPanel == null) {
      myPanel = new IntentionSettingsPanel();
    }
    JPanel component = myPanel.getComponent();
    component.setPreferredSize(new Dimension(800, 600));
    return component;
  }

  public void initUi() {
    if (myPanel == null) {
      myPanel = new IntentionSettingsPanel();
    }
    myPanel.initUi();
  }

  public JComponent getToolbar() {
    return myPanel.getToolbar();
  }

  public JComponent getMaster() {
    return myPanel.getMaster();
  }

  public DetailsComponent getDetails() {
    return myPanel.getDetails();
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getIntentionTree();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  public Icon getIcon() {
    return IconLoader.getIcon("/general/configurableIntentionSettings.png");
  }

  public void reset() {
    myPanel.reset();
  }

  public void apply() {
    myPanel.apply();
  }

  public void disposeUIResources() {
    if (myPanel != null) {
      myPanel.dispose();
    }
    myPanel = null;
  }

  public String getHelpTopic() {
    return HELP_ID;
  }

  public Runnable enableSearch(String option) {
    return myPanel.showOption(this, option);
  }

  public String getId() {
    return HELP_ID;
  }

}
