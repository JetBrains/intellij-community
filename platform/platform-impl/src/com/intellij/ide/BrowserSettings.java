/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
    mySettingsPanel.disposeUIResources();
    mySettingsPanel = null;
  }

}
