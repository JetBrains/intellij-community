/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.application.options;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class JavaCodeStyleMainPanel extends MultiTabCodeStyleAbstractPanel {

  protected JavaCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(currentSettings, settings);
  }

  @Override
  public Language getDefaultLanguage() {
    return JavaLanguage.INSTANCE;
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    super.initTabs(settings);
    addTab(new JavaDocFormattingPanel(settings));
    addTab(new CodeStyleImportsPanelWrapper(settings));
    addTab(new CodeStyleGenerationWrapper(settings));
    for (CodeStyleSettingsProvider provider : Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME)) {
      if (provider.getLanguage() == JavaLanguage.INSTANCE && !provider.hasSettingsPage()) {
        Configurable configurable = provider.createSettingsPage(getCurrentSettings(), settings);
        ConfigurableWrapper wrapper = new ConfigurableWrapper(configurable, settings);
        addTab(wrapper);
      }
    }
    
  }
  
  private class ConfigurableWrapper extends CodeStyleAbstractPanel {
    
    private Configurable myConfigurable;
    
    public ConfigurableWrapper(Configurable configurable, CodeStyleSettings settings) {
      super(settings);
      myConfigurable = configurable;
    }

    @Override
    protected int getRightMargin() {
      return 0;
    }

    @Override
    protected EditorHighlighter createHighlighter(EditorColorsScheme scheme) {
      return null;
    }

    @NotNull
    @Override
    protected FileType getFileType() {
      return JavaFileType.INSTANCE;
    }

    @Override
    protected String getTabTitle() {
      return myConfigurable.getDisplayName();
    }

    @Override
    protected String getPreviewText() {
      return null;
    }

    @Override
    public void apply(CodeStyleSettings settings) {
      try {
        myConfigurable.apply();
      }
      catch (ConfigurationException e) {
        // Ignore
      }
    }

    @Override
    public boolean isModified(CodeStyleSettings settings) {
      return myConfigurable.isModified();
    }

    @Override
    public JComponent getPanel() {
      return myConfigurable.createComponent();
    }

    @Override
    protected void resetImpl(CodeStyleSettings settings) {
      myConfigurable.reset();
    }
  }
}
