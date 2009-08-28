package com.intellij.ide;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author spleaner
 */
public class BrowserSettings implements Configurable, SearchableConfigurable {
  private static final Icon ICON = IconLoader.getIcon("/general/browsersettings.png");
  private BrowserSettingsPanel mySettingsPanel;

  public String getId() {
    return getHelpTopic();
  }

  public Runnable enableSearch(final String option) {
    return null;
  }

  @Nls
  public String getDisplayName() {
    return IdeBundle.message("browsers.settings");
  }

  public Icon getIcon() {
    return ICON;
  }

  public String getHelpTopic() {
    return "reference.settings.ide.settings.web.browsers";
  }

  public JComponent createComponent() {
    if (mySettingsPanel == null) {
      mySettingsPanel = new BrowserSettingsPanel();
    }

    return mySettingsPanel;
  }

  public boolean isModified() {
    return mySettingsPanel != null && mySettingsPanel.isModified();
  }

  public void apply() throws ConfigurationException {
    if (mySettingsPanel != null) {
      mySettingsPanel.apply();
    }
  }

  public void reset() {
    if (mySettingsPanel != null) {
      mySettingsPanel.reset();
    }
  }

  public void disposeUIResources() {
    mySettingsPanel = null;
  }

}
