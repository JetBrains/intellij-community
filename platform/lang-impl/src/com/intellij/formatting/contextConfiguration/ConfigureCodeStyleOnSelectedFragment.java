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
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsCodeFragmentFilter;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCodeFragmentFilter.CodeStyleSettingsToShow;

public class ConfigureCodeStyleOnSelectedFragment implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(ConfigureCodeStyleOnSelectedFragment.class);
  
  @Nls
  @NotNull
  @Override
  public String getText() {
    return "Configure code style";
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return "ConfigureCodeStyleOnSelectedFragment";
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    Language language = file.getLanguage();
    return editor.getSelectionModel().hasSelection() && LanguageCodeStyleSettingsProvider.forLanguage(language) != null;
  }

  @Override
  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    SelectedTextFormatter textFormatter = new SelectedTextFormatter(project, editor, file);
    CodeStyleSettingsToShow settingsToShow = calculateAffectingSettings(editor, file);
    CodeStyleSettings settings = CodeStyleSettingsManager.getSettings(project);
    new FragmentCodeStyleSettingsDialog(project, textFormatter, file.getLanguage(), settings, settingsToShow).show();
  }

  private static CodeStyleSettingsToShow calculateAffectingSettings(@NotNull Editor editor, @NotNull PsiFile file) {
    SelectionModel model = editor.getSelectionModel();
    int start = model.getSelectionStart();
    int end = model.getSelectionEnd();
    CodeStyleSettingsCodeFragmentFilter settingsProvider = new CodeStyleSettingsCodeFragmentFilter(file, new TextRange(start, end));
    return CodeFragmentCodeStyleSettingsPanel.calcSettingNamesToShow(settingsProvider);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
  
  static class FragmentCodeStyleSettingsDialog extends DialogWrapper {
    private final CodeFragmentCodeStyleSettingsPanel myTabbedLanguagePanel;
    private SelectedTextFormatter mySelectedTextFormatter;
    private final CodeStyleSettings mySettings;

    public FragmentCodeStyleSettingsDialog(@NotNull Project project,
                                           @NotNull SelectedTextFormatter selectedTextFormatter,
                                           @NotNull Language language,
                                           CodeStyleSettings settings,
                                           CodeStyleSettingsToShow settingsToShow) {
      super(project, true);
      mySettings = settings;
      mySelectedTextFormatter = selectedTextFormatter;
      myTabbedLanguagePanel = new CodeFragmentCodeStyleSettingsPanel(settings, settingsToShow, language, selectedTextFormatter);
      
      setTitle("Configure Code Style Settings: " + language.getDisplayName());
      setOKButtonText("Save");
      init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      return myTabbedLanguagePanel.getPanel();
    }

    @Override
    protected void dispose() {
      super.dispose();
      Disposer.dispose(myTabbedLanguagePanel);
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

    @Override
    public void doCancelAction() {
      mySelectedTextFormatter.restoreSelectedText();
      super.doCancelAction();
    }
  }
}
