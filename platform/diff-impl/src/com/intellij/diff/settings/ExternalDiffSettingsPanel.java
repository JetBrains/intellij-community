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
package com.intellij.diff.settings;

import com.intellij.diff.tools.external.ExternalDiffSettings;
import com.intellij.openapi.diff.DiffBundle;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExternalDiffSettingsPanel {
  private static final String DESCRIPTION_TEXT =
    "<html>" +
    "Different tools have different parameters. It's important to specify all necessary parameters in proper order<br>" +
    "<b>%1</b> - left (Local changes)<br>" +
    "<b>%2</b> - right (Server content)<br>" +
    "<b>%3</b> - base (Current version without local changes)<br>" +
    "<b>%4</b> - output (Merge result)" +
    "</html>";

  private JPanel myPane;

  @NotNull private final ExternalDiffSettings mySettings = ExternalDiffSettings.getInstance();

  private JCheckBox myDiffEnabled;
  private JBCheckBox myDiffDefault;
  private TextFieldWithBrowseButton myDiffPath;
  private JTextField myDiffParameters;

  private JCheckBox myMergeEnabled;
  private TextFieldWithBrowseButton myMergePath;
  private JTextField myMergeParameters;
  private JLabel myDescriptionLabel;
  private JCheckBox myRespectExitCodeCheckbox;

  public ExternalDiffSettingsPanel() {
    myDescriptionLabel.setText(DESCRIPTION_TEXT);

    myDiffEnabled.getModel().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabledEffect();
      }
    });
    myMergeEnabled.getModel().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabledEffect();
      }
    });

    myDiffPath.addBrowseFolderListener(DiffBundle.message("select.external.diff.program.dialog.title"), null, null,
                                       FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
    myMergePath.addBrowseFolderListener(DiffBundle.message("select.external.merge.program.dialog.title"), null, null,
                                        FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor());
  }

  @NotNull
  public JComponent getPanel() {
    return myPane;
  }

  public boolean isModified() {
    if (mySettings.isDiffEnabled() != myDiffEnabled.isSelected()) return true;
    if (mySettings.isDiffDefault() != myDiffDefault.isSelected()) return true;
    if (!mySettings.getDiffExePath().equals(myDiffPath.getText())) return true;
    if (!mySettings.getDiffParameters().equals(myDiffParameters.getText())) return true;

    if (mySettings.isMergeEnabled() != myMergeEnabled.isSelected()) return true;
    if (!mySettings.getMergeExePath().equals(myMergePath.getText())) return true;
    if (!mySettings.getMergeParameters().equals(myMergeParameters.getText())) return true;
    if (mySettings.isMergeTrustExitCode() != myRespectExitCodeCheckbox.isSelected()) return true;

    return false;
  }

  public void apply() {
    mySettings.setDiffEnabled(myDiffEnabled.isSelected());
    mySettings.setDiffDefault(myDiffDefault.isSelected());
    mySettings.setDiffExePath(myDiffPath.getText());
    mySettings.setDiffParameters(myDiffParameters.getText());

    mySettings.setMergeEnabled(myMergeEnabled.isSelected());
    mySettings.setMergeExePath(myMergePath.getText());
    mySettings.setMergeParameters(myMergeParameters.getText());
    mySettings.setMergeTrustExitCode(myRespectExitCodeCheckbox.isSelected());
  }

  public void reset() {
    myDiffEnabled.setSelected(mySettings.isDiffEnabled());
    myDiffDefault.setSelected(mySettings.isDiffDefault());
    myDiffPath.setText(mySettings.getDiffExePath());
    myDiffParameters.setText(mySettings.getDiffParameters());

    myMergePath.setText(mySettings.getMergeExePath());
    myMergeEnabled.setSelected(mySettings.isMergeEnabled());
    myMergeParameters.setText(mySettings.getMergeParameters());
    myRespectExitCodeCheckbox.setSelected(mySettings.isMergeTrustExitCode());

    updateEnabledEffect();
  }

  private void updateEnabledEffect() {
    UIUtil.setEnabled(myDiffPath, myDiffEnabled.isSelected(), true);
    UIUtil.setEnabled(myDiffParameters, myDiffEnabled.isSelected(), true);
    UIUtil.setEnabled(myDiffDefault, myDiffEnabled.isSelected(), true);

    UIUtil.setEnabled(myMergePath, myMergeEnabled.isSelected(), true);
    UIUtil.setEnabled(myMergeParameters, myMergeEnabled.isSelected(), true);
    UIUtil.setEnabled(myRespectExitCodeCheckbox, myMergeEnabled.isSelected(), true);
  }

  private void createUIComponents() {
  }
}
