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
package com.intellij.psi.codeStyle.extractor.ui;

import com.intellij.application.options.CodeStyle;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.application.options.codeStyle.CodeStyleMainPanel;
import com.intellij.application.options.codeStyle.CodeStyleSchemesModelListener;
import com.intellij.application.options.codeStyle.NewCodeStyleSettingsPanel;
import com.intellij.diff.DiffContentFactoryEx;
import com.intellij.diff.DiffManager;
import com.intellij.diff.DiffRequestPanel;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.ContentDiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.ide.actions.ShowSettingsUtilImpl;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleScheme;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.extractor.differ.LangCodeStyleExtractor;
import com.intellij.psi.codeStyle.extractor.values.ValuesExtractionResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ExtractPreviewDialog extends DialogWrapper {
  @NotNull final PsiFile myFile;
  @NotNull final ValuesExtractionResult myExtractionResult;
  @NotNull final String myReport;
  
  public ExtractPreviewDialog(@NotNull PsiFile file, @NotNull ValuesExtractionResult result, @NotNull String report) {
    super(file.getProject());
    myFile = file;
    myExtractionResult = result;
    myReport = report;
    setTitle(String.format("Extracted from the %s file code style", file.getName()));
    setOKButtonText("Accept Extracted Code Style As Project default");
    //setCancelButtonText("Keep Original Code Style");
    init();
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    return new JLabel("<html>" + myReport + "</html>");
  }

  private static final float DIFF_SPLITTER_PROPORTION = 0.5f;
  private static final String DIFF_SPLITTER_PROPORTION_KEY = "code.style.extractor";
  
  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return getDiffComponent();
    //JBSplitter mySplitter = new JBSplitter(true, DIFF_SPLITTER_PROPORTION_KEY, DIFF_SPLITTER_PROPORTION);
    //mySplitter.setFirstComponent(getCodeFormat());
    //mySplitter.setSecondComponent(getDiffComponent());
    //return mySplitter;
  }

  @NotNull
  private JComponent getCodeFormat() {
    final String languageName = myFile.getLanguage().getDisplayName();
    String configurableId = "preferences.sourceCode." + languageName;
    final Project project = myFile.getProject();
    final Configurable configurable =
      new ConfigurableVisitor.ByID(configurableId).find(ShowSettingsUtilImpl.getConfigurableGroups(project, true));

    if(configurable != null) {
      CodeStyleMainPanel codeStyleMainPanel = (CodeStyleMainPanel)configurable.createComponent();
      if (codeStyleMainPanel != null) {
        codeStyleMainPanel.getModel().addListener(new CodeStyleSchemesModelListener() {
          @Override
          public void currentSchemeChanged(Object source) {
            int i = 43;
          }

          @Override
          public void schemeListChanged() {
            int i = 43;
          }

          @Override
          public void beforeCurrentSettingsChanged() {
            int i = 43;
          }

          @Override
          public void afterCurrentSettingsChanged() {
            if (codeStyleMainPanel.isModified()) {
              final CodeStyleSettings settings = codeStyleMainPanel.getModel().getSelectedScheme().getCodeStyleSettings();
              myDiffPanel.setRequest(createDiffRequest(settings), new Object());
            }
          }

          @Override
          public void schemeChanged(CodeStyleScheme scheme) {
            int i = 43;
          }

          @Override
          public void settingsChanged(@NotNull CodeStyleSettings settings) {
            myDiffPanel.setRequest(createDiffRequest(settings));
          }
        });
        if (codeStyleMainPanel.getPanels() != null) {
          for (NewCodeStyleSettingsPanel p : codeStyleMainPanel.getPanels()) {
            CodeStyleAbstractPanel selectedPanel = p.getSelectedPanel();
            if (selectedPanel instanceof TabbedLanguageCodeStylePanel) {
              final TabbedLanguageCodeStylePanel panel = (TabbedLanguageCodeStylePanel)selectedPanel;
              panel.changeTab(ApplicationBundle.message("title.tabs.and.indents"));
            }
          }
        }
        return codeStyleMainPanel;
      }
    }
    return new JLabel("No Code Style Dialog For " + languageName);
  }

  DiffRequestPanel myDiffPanel;
  
  @NotNull
  private JComponent getDiffComponent() {
    myDiffPanel = DiffManager.getInstance().createRequestPanel(myFile.getProject(), getDisposable(), null);
    myDiffPanel.setRequest(createDiffRequest(CodeStyle.getSettings(myFile)));
    return myDiffPanel.getComponent();
  }

  @NotNull
  private ContentDiffRequest createDiffRequest(@NotNull CodeStyleSettings settings) {
    final LangCodeStyleExtractor extractor = LangCodeStyleExtractor.EXTENSION.forLanguage(myFile.getLanguage());

    final ValuesExtractionResult orig = myExtractionResult.apply(true);
    final Project project = myFile.getProject();
    final String reformattedText = extractor.getDiffer(project, myFile, settings).reformattedText();
    orig.apply(false);

    DiffContentFactoryEx contentFactory = DiffContentFactoryEx.getInstanceEx();
    final DiffContent origCtx = contentFactory.create(project, myFile.getVirtualFile());
    final DiffContent newCtx = contentFactory.create(project, reformattedText);

    return new SimpleDiffRequest(null, origCtx, newCtx, "Original Code", "Code reformatted with Extracted Code Style");
  }
}
