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

package com.intellij.tools;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;

public class ToolConfigurable implements SearchableConfigurable, Configurable.NoScroll {
  private BaseToolsPanel myPanel;

  @Override
  public String getDisplayName() {
    return ToolsBundle.message("tools.settings.title");
  }

  @Override
  public JComponent createComponent() {
    myPanel = new ToolsPanel();
    return myPanel;
  }

  @Override
  public void apply() throws ConfigurationException {
    try {
      myPanel.apply();
    }
    catch (IOException e) {
      throw new ConfigurationException(e.getMessage());
    }
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void reset() {
    myPanel.reset();
  }

  @Override
  public void disposeUIResources() {
    myPanel = null;
  }

  @Override
  public String getHelpTopic() {
    return "preferences.externalTools";
  }


  @Override
  @NotNull
  public String getId() {
    return getHelpTopic();
  }

  @Override
  @Nullable
  public Runnable enableSearch(String option) {
    return null;
  }
}
