/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.diff.settings;

import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.options.SearchableConfigurable;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ExternalDiffSettingsConfigurable implements SearchableConfigurable {
  private ExternalDiffSettingsPanel mySettingsPane;

  @NotNull
  @Override
  public String getId() {
    return getHelpTopic();
  }

  @Nls
  @Override
  public String getDisplayName() {
    return DiffBundle.message("configurable.ExternalDiffSettingsConfigurable.display.name");
  }

  @NotNull
  @Override
  public String getHelpTopic() {
    return "diff.external";
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    if (mySettingsPane == null) {
      mySettingsPane = new ExternalDiffSettingsPanel();
    }
    return mySettingsPane.createComponent();
  }

  @Override
  public boolean isModified() {
    return mySettingsPane != null && mySettingsPane.isModified();
  }

  @Override
  public void apply() {
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
