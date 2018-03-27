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
package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Eugene Zhuravlev
 */
public class ContentEntriesEditor extends JavaContentEntriesEditor {
  private LanguageLevelConfigurable myLanguageLevelConfigurable;

  public ContentEntriesEditor(String moduleName, final ModuleConfigurationState state) {
    super(moduleName, state);
  }

  @Override
  public void disposeUIResources() {
    if (myLanguageLevelConfigurable != null) myLanguageLevelConfigurable.disposeUIResources();
    super.disposeUIResources();
  }

  @Override
  public boolean isModified() {
    return super.isModified() || myLanguageLevelConfigurable != null && myLanguageLevelConfigurable.isModified();
  }

  @Override
  protected void addAdditionalSettingsToPanel(final JPanel mainPanel) {
    myLanguageLevelConfigurable = new LanguageLevelConfigurable(myProject, this::fireConfigurationChanged) {
      @NotNull
      @Override
      public LanguageLevelModuleExtensionImpl getLanguageLevelExtension() {
        return getModel().getModuleExtension(LanguageLevelModuleExtensionImpl.class);
      }
    };
    mainPanel.add(myLanguageLevelConfigurable.createComponent(), BorderLayout.NORTH);
    myLanguageLevelConfigurable.reset();
  }

  @Override
  public void apply() throws ConfigurationException {
    myLanguageLevelConfigurable.apply();
    super.apply();
  }
}
