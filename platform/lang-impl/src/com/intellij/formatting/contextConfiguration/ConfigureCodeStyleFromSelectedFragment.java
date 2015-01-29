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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.Language;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.extensions.Extensions;
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
    return editor.getSelectionModel().hasSelection() && getSettingsProvider(file.getLanguage()) != null;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    Language language = file.getLanguage();
    CodeStyleSettingsProvider languageSettingsProvider = getSettingsProvider(language);
    
    if (languageSettingsProvider == null) {
      LOG.info("No code style settings provider found for " + language);
    }
    
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);

    if (languageSettingsProvider instanceof CodeFragmentSettingProvider) {
      CodeFragmentSettingProvider fragmentSettingProvider = (CodeFragmentSettingProvider)languageSettingsProvider;
      JComponent viewer = fragmentSettingProvider.createSettingsForSelectedFragment(editor, settings, settings);
      DialogWrapper dialog = new FragmentCodeStyleSettingsDialog(project, viewer);
      dialog.show();
    }
  }

  @Nullable
  private CodeStyleSettingsProvider getSettingsProvider(Language language) {
    CodeStyleSettingsProvider forLanguage = null;
    
    CodeStyleSettingsProvider[] extensions = Extensions.getExtensions(CodeStyleSettingsProvider.EXTENSION_POINT_NAME);
    for (CodeStyleSettingsProvider extension : extensions) {
      if (language.is(extension.getLanguage()) && extension.hasSettingsPage()) {
        forLanguage = extension;
        break;
      }
    }
    
    return forLanguage;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}


class FragmentCodeStyleSettingsDialog extends DialogWrapper {
  private final JComponent myComponent;

  public FragmentCodeStyleSettingsDialog(@NotNull Project project, @NotNull JComponent component) {
    super(project, true);
    myComponent = component;
    setTitle("Configure Code Style Settings on Selected Fragment");
    setOKButtonText("Save");
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myComponent;
  }

}
