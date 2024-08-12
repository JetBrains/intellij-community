// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.arrangement.Rearranger;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.ui.components.JBCheckBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class LayoutCodeDialog extends DialogWrapper {
  private final PsiFile myFile;
  private final boolean myTextSelected;
  private final String myHelpId;
  private final LastRunReformatCodeOptionsProvider myLastRunOptions;
  private final LayoutCodeOptions myRunOptions;

  private JPanel myButtonsPanel;
  private JCheckBox myOptimizeImportsCb;
  private JCheckBox myRearrangeCodeCb;
  private JCheckBox myApplyCodeCleanup;
  private JRadioButton myOnlyVCSChangedTextRb;
  private JRadioButton mySelectedTextRadioButton;
  private JRadioButton myWholeFileRadioButton;
  private JBCheckBox myDoNotKeepLineBreaks;

  public LayoutCodeDialog(@NotNull Project project, @NotNull PsiFile file, boolean textSelected, String helpId) {
    super(project, true);
    myFile = file;
    myTextSelected = textSelected;
    myHelpId = helpId;

    myLastRunOptions = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());
    myRunOptions = createOptionsBundledOnDialog();

    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle(CodeInsightBundle.message("dialog.title.reformat.file.0", file.getName()));

    init();
  }

  @Override
  protected void init() {
    super.init();

    setUpActions();
    setUpTextRangeMode();
  }


  private void setUpTextRangeMode() {
    mySelectedTextRadioButton.setEnabled(myTextSelected);
    if (!myTextSelected) {
      mySelectedTextRadioButton.setToolTipText(CodeInsightBundle.message("tooltip.no.text.selected.in.editor"));
    }

    final boolean fileHasChanges = VcsFacade.getInstance().hasChanges(myFile);
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
      boolean lastRunProcessedChangedText = myLastRunOptions.getLastTextRangeType() == TextRangeType.VCS_CHANGED_TEXT;
      if (lastRunProcessedChangedText && fileHasChanges) {
        myOnlyVCSChangedTextRb.setSelected(true);
      }
      else {
        myWholeFileRadioButton.setSelected(true);
      }
    }
  }

  private void setUpActions() {
    boolean canOptimizeImports = !OptimizeImportsProcessor.collectOptimizers(myFile).isEmpty();
    myOptimizeImportsCb.setVisible(canOptimizeImports);
    if (canOptimizeImports) {
      myOptimizeImportsCb.setSelected(myLastRunOptions.getLastOptimizeImports());
    }

    boolean canRearrangeCode = Rearranger.EXTENSION.forLanguage(myFile.getLanguage()) != null;
    myRearrangeCodeCb.setVisible(canRearrangeCode);
    if (canRearrangeCode) {
      myRearrangeCodeCb.setSelected(myLastRunOptions.isRearrangeCode(myFile.getLanguage()));
    }

    myApplyCodeCleanup.setSelected(myLastRunOptions.getLastCodeCleanup());

    boolean keepLineBreaks = CodeStyle.getLanguageSettings(myFile).KEEP_LINE_BREAKS;
    myDoNotKeepLineBreaks.setVisible(keepLineBreaks);
    if (keepLineBreaks) {
      myDoNotKeepLineBreaks.setSelected(myLastRunOptions.isDoNotKeepLineBreaks());
    }
  }

  private @Nullable @NlsContexts.Tooltip String getChangesNotAvailableHint() {
    if (!VcsFacade.getInstance().isFileUnderVcs(myFile)) {
      return CodeInsightBundle.message("tooltip.file.not.under.vcs.root");
    }
    else if (!VcsFacade.getInstance().hasChanges(myFile)) {
      return CodeInsightBundle.message("tooltip.file.was.not.changed.since.last.revision");
    }
    return null;
  }

  private void saveCurrentConfiguration() {
    if (myOptimizeImportsCb.isEnabled() && myOptimizeImportsCb.isVisible()) {
      myLastRunOptions.saveOptimizeImportsState(myRunOptions.isOptimizeImports());
    }
    if (myRearrangeCodeCb.isEnabled()) {
      myLastRunOptions.saveRearrangeState(myFile.getLanguage(), myRunOptions.isRearrangeCode());
    }
    if (myApplyCodeCleanup.isEnabled()) {
      myLastRunOptions.saveCodeCleanupState(myApplyCodeCleanup.isSelected());
    }

    if (!mySelectedTextRadioButton.isSelected() && myOnlyVCSChangedTextRb.isEnabled()) {
      myLastRunOptions.saveProcessVcsChangedTextState(myOnlyVCSChangedTextRb.isSelected());
    }
    if (myDoNotKeepLineBreaks.isVisible()) {
      myLastRunOptions.setDoNotKeepLineBreaks(myDoNotKeepLineBreaks.isSelected());
    }
  }

  private LayoutCodeOptions createOptionsBundledOnDialog() {
    return new LayoutCodeOptions() {
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

      @Override
      public boolean isRearrangeCode() {
        return myRearrangeCodeCb.isEnabled() && myRearrangeCodeCb.isSelected();
      }

      @Override
      public boolean isOptimizeImports() {
        return myOptimizeImportsCb.isEnabled() && myOptimizeImportsCb.isSelected();
      }

      @Override
      public boolean isCodeCleanup() {
        return myApplyCodeCleanup.isEnabled() && myApplyCodeCleanup.isSelected();
      }

      @Override
      public boolean doNotKeepLineBreaks() {
        return myDoNotKeepLineBreaks.isEnabled() && myDoNotKeepLineBreaks.isSelected();
      }
    };
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    return myButtonsPanel;
  }

  @Override
  protected @Nullable String getHelpId() {
    return myHelpId;
  }

  @Override
  protected void doOKAction() {
    saveCurrentConfiguration();
    super.doOKAction();
  }

  public LayoutCodeOptions getRunOptions() {
    return myRunOptions;
  }
}