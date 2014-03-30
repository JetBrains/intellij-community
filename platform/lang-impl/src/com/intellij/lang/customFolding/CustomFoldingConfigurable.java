package com.intellij.lang.customFolding;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class CustomFoldingConfigurable implements SearchableConfigurable {

  private final CustomFoldingConfiguration myConfiguration;
  private final CustomFoldingSettingsPanel mySettingsPanel;

  public CustomFoldingConfigurable(Project project) {
    myConfiguration = CustomFoldingConfiguration.getInstance(project);
    mySettingsPanel = new CustomFoldingSettingsPanel();
  }

  @NotNull
  @Override
  public String getId() {
    return getDisplayName();
  }

  @Override
  public Runnable enableSearch(String option) {
    return null;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return "Custom Folding"; //TODO<rv> Move to resources
  }

  @Override
  public String getHelpTopic() {
    return null; //TODO<rv>: Define help topic
  }

  @Override
  public JComponent createComponent() {
    return mySettingsPanel.getComponent();
  }

  @Override
  public boolean isModified() {
    return myConfiguration.getState().isEnabled() != mySettingsPanel.isEnabled() ||
           !myConfiguration.getState().getStartFoldingPattern().equals(mySettingsPanel.getStartPattern()) ||
           !myConfiguration.getState().getEndFoldingPattern().equals(mySettingsPanel.getEndPattern()) ||
           !myConfiguration.getState().getDefaultCollapsedStatePattern().equals(mySettingsPanel.getCollapsedStatePattern());
  }

  @Override
  public void apply() throws ConfigurationException {
    myConfiguration.getState().setStartFoldingPattern(mySettingsPanel.getStartPattern());
    myConfiguration.getState().setEndFoldingPattern(mySettingsPanel.getEndPattern());
    myConfiguration.getState().setEnabled(mySettingsPanel.isEnabled());
    myConfiguration.getState().setDefaultCollapsedStatePattern(mySettingsPanel.getCollapsedStatePattern());
  }

  @Override
  public void reset() {
    mySettingsPanel.setStartPattern(myConfiguration.getState().getStartFoldingPattern());
    mySettingsPanel.setEndPattern(myConfiguration.getState().getEndFoldingPattern());
    mySettingsPanel.setEnabled(myConfiguration.getState().isEnabled());
    mySettingsPanel.setCollapsedStatePattern(myConfiguration.getState().getDefaultCollapsedStatePattern());
  }

  @Override
  public void disposeUIResources() {
  }
}
