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

package com.intellij.codeInsight.actions;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.find.FindSettings;
import com.intellij.find.impl.FindDialog;
import com.intellij.find.impl.FindInProjectUtil;
import com.intellij.icons.AllIcons;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.scopeChooser.ScopeChooserCombo;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.search.SearchScope;
import com.intellij.ui.IdeBorderFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.*;
import java.util.regex.PatternSyntaxException;

/**
 * @author max
 */
public class LayoutProjectCodeDialog extends DialogWrapper implements ReformatFilesOptions {
  private static @NonNls final String HELP_ID = "Reformat Code on Directory Dialog";

  private final Project myProject;
  private final String  myText;
  private final boolean myEnableOnlyVCSChangedTextCb;
  private final LastRunReformatCodeOptionsProvider myLastRunOptions;

  private JLabel myTitle;
  protected JCheckBox myIncludeSubdirsCb;

  private JCheckBox myUseScopeFilteringCb;
  private ScopeChooserCombo myScopeCombo;

  private JCheckBox myEnableFileNameFilterCb;
  private ComboBox myFileFilter;

  private JCheckBox myCbOptimizeImports;
  private JCheckBox myCbRearrangeEntries;
  private JCheckBox myCbOnlyVcsChangedRegions;

  private JPanel myWholePanel;
  private JPanel myOptionsPanel;
  private JPanel myFiltersPanel;
  private JLabel myMaskWarningLabel;

  public LayoutProjectCodeDialog(@NotNull Project project,
                                 @NotNull String title,
                                 @NotNull String text,
                                 boolean enableOnlyVCSChangedTextCb)
  {
    super(project, false);
    myText = text;
    myProject = project;
    myEnableOnlyVCSChangedTextCb = enableOnlyVCSChangedTextCb;
    myLastRunOptions = new LastRunReformatCodeOptionsProvider(PropertiesComponent.getInstance());

    setOKButtonText(CodeInsightBundle.message("reformat.code.accept.button.text"));
    setTitle(title);
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myTitle.setText(myText);
    myOptionsPanel.setBorder(IdeBorderFactory.createTitledBorder(CodeInsightBundle.message("reformat.directory.dialog.options")));
    myFiltersPanel.setBorder(IdeBorderFactory.createTitledBorder(CodeInsightBundle.message("reformat.directory.dialog.filters")));

    myMaskWarningLabel.setIcon(AllIcons.General.Warning);
    myMaskWarningLabel.setVisible(false);

    myIncludeSubdirsCb.setVisible(shouldShowIncludeSubdirsCb());

    initFileTypeFilter();
    initScopeFilter();

    restoreCbsStates();
    return myWholePanel;
  }

  private void restoreCbsStates() {
    myCbOptimizeImports.setSelected(myLastRunOptions.getLastOptimizeImports());
    myCbRearrangeEntries.setSelected(myLastRunOptions.getLastRearrangeCode());
    myCbOnlyVcsChangedRegions.setEnabled(myEnableOnlyVCSChangedTextCb);
    myCbOnlyVcsChangedRegions.setSelected(
      myEnableOnlyVCSChangedTextCb && myLastRunOptions.getLastTextRangeType() == TextRangeType.VCS_CHANGED_TEXT
    );
  }

  private void initScopeFilter() {
    myUseScopeFilteringCb.setSelected(false);
    myScopeCombo.setEnabled(false);
    myUseScopeFilteringCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myScopeCombo.setEnabled(myUseScopeFilteringCb.isSelected());
      }
    });
  }

  private void initFileTypeFilter() {
    FindDialog.initFileFilter(myFileFilter, myEnableFileNameFilterCb);
    myEnableFileNameFilterCb.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateMaskWarning();
      }
    });
    myFileFilter.getEditor().getEditorComponent().addKeyListener(new KeyAdapter() {
      @Override
      public void keyReleased(KeyEvent e) {
        updateMaskWarning();
      }
    });
  }

  private void updateMaskWarning() {
    if (myEnableFileNameFilterCb.isSelected()) {
      String mask = (String)myFileFilter.getEditor().getItem();
      if (mask == null || !isMaskValid(mask)) {
        showWarningAndDisableOK();
        return;
      }
    }

    if (myMaskWarningLabel.isVisible()) {
      clearWarningAndEnableOK();
    }
  }

  private void showWarningAndDisableOK() {
    myMaskWarningLabel.setVisible(true);
    setOKActionEnabled(false);
  }

  private void clearWarningAndEnableOK() {
    myMaskWarningLabel.setVisible(false);
    setOKActionEnabled(true);
  }

  private static boolean isMaskValid(@NotNull String mask) {
    try {
      FindInProjectUtil.createFileMaskRegExp(mask);
    }
    catch (PatternSyntaxException e) {
      return false;
    }

    return true;
  }

  @NotNull
  @Override
  protected Action[] createActions() {
    return new Action[]{getOKAction(), getCancelAction(), getHelpAction()};
  }

  @Override
  public boolean isRearrangeCode() {
    return myCbRearrangeEntries.isSelected();
  }

  @Override
  protected void doHelpAction() {
    HelpManager.getInstance().invokeHelp(HELP_ID);
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
    myLastRunOptions.saveOptimizeImportsState(isOptimizeImports());
    myLastRunOptions.saveRearrangeCodeState(isRearrangeCode());
    if (myEnableOnlyVCSChangedTextCb) {
      myLastRunOptions.saveProcessVcsChangedTextState(getTextRangeType() == TextRangeType.VCS_CHANGED_TEXT);
    }
  }

  public boolean isOptimizeImports() {
    return myCbOptimizeImports.isSelected();
  }

  @Nullable
  public String getFileTypeMask() {
    if (myEnableFileNameFilterCb.isSelected()) {
      return (String)myFileFilter.getSelectedItem();
    }

    return null;
  }

  protected void createUIComponents() {
    myScopeCombo = new ScopeChooserCombo(myProject, false, false, FindSettings.getInstance().getDefaultScopeName());
    Disposer.register(myDisposable, myScopeCombo);
  }

  @Nullable
  @Override
  public SearchScope getSearchScope() {
    if (myUseScopeFilteringCb.isSelected()) {
      return myScopeCombo.getSelectedScope();
    }

    return null;
  }

  protected boolean shouldShowIncludeSubdirsCb() {
    return false;
  }

  @Override
  public TextRangeType getTextRangeType() {
    return myCbOnlyVcsChangedRegions.isEnabled() && myCbOnlyVcsChangedRegions.isSelected()
           ? TextRangeType.VCS_CHANGED_TEXT
           : TextRangeType.WHOLE_FILE;
  }
}
