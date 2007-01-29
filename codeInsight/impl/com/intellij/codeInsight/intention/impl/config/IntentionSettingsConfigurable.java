package com.intellij.codeInsight.intention.impl.config;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;
import java.awt.*;

public class IntentionSettingsConfigurable extends BaseConfigurable implements SearchableConfigurable {
  private IntentionSettingsPanel myPanel;

  public JComponent createComponent() {
    myPanel = new IntentionSettingsPanel();
    JPanel component = myPanel.getComponent();
    component.setPreferredSize(new Dimension(800, 600));
    return component;
  }

  public JComponent getPreferredFocusedComponent() {
    return myPanel.getIntentionTree();
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public String getDisplayName() {
    return CodeInsightBundle.message("intention.settings");
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
  }

  public String getHelpTopic() {
    return "preferences.intentionPowerPack";
  }

  public Runnable enableSearch(String option) {
    return myPanel.showOption(this, option);
  }

  public String getId() {
    return getHelpTopic();
  }

  public boolean clearSearch() {
    myPanel.clearSearch();
    return true;
  }

}