package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.ui.search.SearchUtil;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.options.BaseConfigurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.options.ex.GlassPanel;
import com.intellij.openapi.util.IconLoader;

import javax.swing.*;

public class KeymapConfigurable extends BaseConfigurable implements SearchableConfigurable, ApplicationComponent {
  private static final Icon icon = IconLoader.getIcon("/general/keymap.png");
  private KeymapPanel myPanel;
  private GlassPanel myGlassPanel;

  public String getComponentName() {
    return "KeymapConfigurable";
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  public String getDisplayName() {
    return KeyMapBundle.message("keymap.display.name");
  }

  public JComponent createComponent() {
    myPanel = new KeymapPanel();
    myGlassPanel = new GlassPanel(myPanel);
    return myPanel;
  }

  public boolean isModified() {
    return myPanel.isModified();
  }

  public Icon getIcon() {
    return icon;
  }

  public void apply() throws ConfigurationException {
    myPanel.apply();
  }

  public void reset() {
    myPanel.getRootPane().setGlassPane(myGlassPanel);
    myPanel.reset();
  }

  public void disposeUIResources() {
    myPanel = null;
  }

  public String getHelpTopic() {
    return "preferences.keymap";
  }

  public void selectAction(String actionId) {
    myPanel.selectAction(actionId);
  }

  public Runnable showOption(String option) {
    return SearchUtil.lightOptions(myPanel, option, myGlassPanel);
  }

  public String getId() {
    return getHelpTopic();
  }

  public void clearSearch() {
    myGlassPanel.clear();
  }
}