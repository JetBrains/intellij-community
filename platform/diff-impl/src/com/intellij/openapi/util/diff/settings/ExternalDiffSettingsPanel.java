package com.intellij.openapi.util.diff.settings;

import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.util.diff.tools.external.ExternalDiffSettings;
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

  public ExternalDiffSettingsPanel() {
    myDescriptionLabel.setText(DESCRIPTION_TEXT);

    myDiffEnabled.getModel().addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        updateEnabledEffect();
      }
    });
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
  }

  public void reset() {
    myDiffEnabled.setSelected(mySettings.isDiffEnabled());
    myDiffDefault.setSelected(mySettings.isDiffDefault());
    myDiffPath.setText(mySettings.getDiffExePath());
    myDiffParameters.setText(mySettings.getDiffParameters());

    myMergePath.setText(mySettings.getMergeExePath());
    myMergeEnabled.setSelected(mySettings.isMergeEnabled());
    myMergeParameters.setText(mySettings.getMergeParameters());

    updateEnabledEffect();
  }

  private void updateEnabledEffect() {
    UIUtil.setEnabled(myDiffPath, myDiffEnabled.isSelected(), true);
    UIUtil.setEnabled(myDiffParameters, myDiffEnabled.isSelected(), true);
    UIUtil.setEnabled(myDiffDefault, myDiffEnabled.isSelected(), true);

    UIUtil.setEnabled(myMergePath, myMergeEnabled.isSelected(), true);
    UIUtil.setEnabled(myMergeParameters, myMergeEnabled.isSelected(), true);
  }

  private void createUIComponents() {
  }
}
