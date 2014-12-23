/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.lang.LanguageImportStatements;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class LayoutCodeDialog extends DialogWrapper implements LayoutCodeOptions {
  @NotNull  private final Project myProject;
  @NotNull private final PsiFile myFile;

  private final boolean myTextSelected;

  private final String myHelpId;
  private final LastRunReformatCodeOptionsProvider myLastRunOptions;

  private JPanel myButtonsPanel;

  private JCheckBox myOptimizeImportsCb;
  private JCheckBox myRearrangeCodeCb;

  private JRadioButton myOnlyVCSChangedTextRb;
  private JRadioButton mySelectedTextRadioButton;
  private JRadioButton myWholeFileRadioButton;

  private JPanel myActionsPanel;
  private JPanel myScopePanel;

  public LayoutCodeDialog(@NotNull Project project,
                          @NotNull PsiFile file,
                          boolean textSelected,
                          final String helpId) {
    super(project, true);
    myFile = file;
    myProject = project;
    myTextSelected = textSelected;
    myHelpId = helpId;

    myLastRunOptions = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());

    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle("Reformat File: " + file.getName());

    init();
  }

  protected void init() {
    super.init();

    setUpActions();
    setUpTextRangeMode();
  }

  @Override
  public TextRangeType getTextRangeType() {
    if (myOnlyVCSChangedTextRb.isSelected()) {
      return TextRangeType.VCS_CHANGED_TEXT;
    }
    if (mySelectedTextRadioButton.isSelected()) {
      return TextRangeType.SELECTED_TEXT;
    }
    return TextRangeType.WHOLE_FILE;
  }

  private void setUpTextRangeMode() {
    mySelectedTextRadioButton.setEnabled(myTextSelected);
    if (!myTextSelected) {
      mySelectedTextRadioButton.setToolTipText("No text selected in editor");
    }

    final boolean fileHasChanges = FormatChangedTextUtil.hasChanges(myFile);
    if (myFile.getVirtualFile() instanceof LightVirtualFile) {
      myOnlyVCSChangedTextRb.setVisible(false);
    }
    else {
      myOnlyVCSChangedTextRb.setEnabled(fileHasChanges);
      if (!fileHasChanges) {
        String hint = getChangesNotAvailableHint();
        if (hint != null) myOnlyVCSChangedTextRb.setToolTipText(hint);
      }
    }

    myWholeFileRadioButton.setEnabled(true);

    if (myTextSelected) {
      mySelectedTextRadioButton.setSelected(true);
    }
    else {
      boolean lastRunProcessedChangedText = myLastRunOptions.getTextRangeType() == TextRangeType.VCS_CHANGED_TEXT;
      if (lastRunProcessedChangedText && fileHasChanges) {
        myOnlyVCSChangedTextRb.setSelected(true);
      }
      else {
        myWholeFileRadioButton.setSelected(true);
      }
    }
  }

  private void setUpActions() {
    boolean canOptimizeImports = !LanguageImportStatements.INSTANCE.forFile(myFile).isEmpty();
    myOptimizeImportsCb.setVisible(canOptimizeImports);
    if (canOptimizeImports) {
      myOptimizeImportsCb.setSelected(myLastRunOptions.isOptimizeImports());
    }

    boolean canRearrangeCode = Rearranger.EXTENSION.forLanguage(myFile.getLanguage()) != null;
    myRearrangeCodeCb.setVisible(canRearrangeCode);
    if (canRearrangeCode) {
      myRearrangeCodeCb.setSelected(myLastRunOptions.isRearrangeCode(myFile.getLanguage()));
    }
  }

  @Nullable
  private String getChangesNotAvailableHint() {
    if (!VcsUtil.isFileUnderVcs(myProject, VcsUtil.getFilePath(myFile.getVirtualFile()))) {
      return "File not under VCS root";
    }
    else if (!FormatChangedTextUtil.hasChanges(myFile)) {
      return "File was not changed since last revision";
    }
    return null;
  }

  private void saveCurrentConfiguration() {
    if (myOptimizeImportsCb.isEnabled()) {
      myLastRunOptions.saveOptimizeImportsState(isOptimizeImports());
    }
    if (myRearrangeCodeCb.isEnabled()) {
      myLastRunOptions.saveRearrangeState(myFile.getLanguage(), isRearrangeCode());
    }

    if (!mySelectedTextRadioButton.isSelected()) {
      myLastRunOptions.saveProcessVcsChangedTextState(myOnlyVCSChangedTextRb.isSelected());
    }
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myButtonsPanel;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(myHelpId);
  }

  @Override
  public boolean isRearrangeCode() {
    return myRearrangeCodeCb.isEnabled() && myRearrangeCodeCb.isSelected();
  }

  @Override
  public boolean isOptimizeImports() {
    return myOptimizeImportsCb.isEnabled() && myOptimizeImportsCb.isSelected();
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    saveCurrentConfiguration();
  }
}
