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
  private JPanel myPane;

  @NotNull private final ExternalDiffSettings mySettings = ExternalDiffSettings.getInstance();

  private JCheckBox myFilesEnabled;
  private TextFieldWithBrowseButton myFilePath;
  private JTextField myFileParameters;
  private JBCheckBox myFileDefault;

  public ExternalDiffSettingsPanel() {
    myFilesEnabled.getModel().addActionListener(new ActionListener() {
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
    if (mySettings.isEnabled() != myFilesEnabled.isSelected()) return true;
    if (mySettings.isDefault() != myFileDefault.isSelected()) return true;
    if (!mySettings.getExePath().equals(myFilePath.getText())) return true;
    if (!mySettings.getParameters().equals(myFileParameters.getText())) return true;

    return false;
  }

  public void apply() {
    mySettings.setEnabled(myFilesEnabled.isSelected());
    mySettings.setDefault(myFileDefault.isSelected());
    mySettings.setExePath(myFilePath.getText());
    mySettings.setParameters(myFileParameters.getText());
  }

  public void reset() {
    myFilesEnabled.setSelected(mySettings.isEnabled());
    myFileDefault.setSelected(mySettings.isDefault());
    myFilePath.setText(mySettings.getExePath());
    myFileParameters.setText(mySettings.getParameters());

    updateEnabledEffect();
  }

  private void updateEnabledEffect() {
    UIUtil.setEnabled(myFilePath, myFilesEnabled.isSelected(), true);
    UIUtil.setEnabled(myFileParameters, myFilesEnabled.isSelected(), true);
    UIUtil.setEnabled(myFileDefault, myFilesEnabled.isSelected(), true);
  }

  private void createUIComponents() {
  }
}
