/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.formatting.contextConfiguration;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.*;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ConfigureCodeStyleFromSelectedFragment implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(ConfigureCodeStyleFromSelectedFragment.class); 
  
  @Nls
  @NotNull
  @Override
  public String getText() {
    return "Configure code style on selected fragment";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "CodeStyle";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Language language = file.getLanguage();
    return editor.getSelectionModel().hasSelection() && LanguageCodeStyleSettingsProvider.forLanguage(language) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    TabbedLanguageCodeStylePanel fragment = new CodeFragmentCodeStyleSettingsPanel(settings, project, editor, file);
    new FragmentCodeStyleSettingsDialog(project, fragment, settings).show();
  }
  
  @Override
  public boolean startInWriteAction() {
    return false;
  }
  
  static class FragmentCodeStyleSettingsDialog extends DialogWrapper {
    private final TabbedLanguageCodeStylePanel myTabbedLanguagePanel;
    private final CodeStyleSettings mySettings;

    public FragmentCodeStyleSettingsDialog(@NotNull Project project, TabbedLanguageCodeStylePanel component, CodeStyleSettings settings) {
      super(project, true);
      myTabbedLanguagePanel = component;
      mySettings = settings;
      
      setTitle("Configure Code Style Settings on Selected Fragment");
      setOKButtonText("Save");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myTabbedLanguagePanel.getPanel();
    }

    @Override
    protected void doOKAction() {
      try {
        myTabbedLanguagePanel.apply(mySettings);
      }
      catch (ConfigurationException e) {
        LOG.debug("Can not apply code style settings from context menu to project code style settings");
      }
      super.doOKAction();
    }
  }
}
