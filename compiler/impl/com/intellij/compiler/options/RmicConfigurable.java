package com.intellij.compiler.options;

import com.intellij.compiler.RmicSettings;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class RmicConfigurable implements Configurable{
  private JPanel myPanel;
  private JCheckBox myCbEnabled;
  private JCheckBox myCbGenerateIiopStubs;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbGenerateNoWarnings;
  private RawCommandLineEditor myAdditionalOptionsField;
  private RmicSettings myRmicSettings;
  private JLabel myFieldLabel;

  public RmicConfigurable(final RmicSettings javacSettings) {
    myRmicSettings = javacSettings;
    myCbEnabled.addItemListener(new ItemListener() {
      public void itemStateChanged(ItemEvent e) {
        setOptionsEnabled(e.getStateChange() == ItemEvent.SELECTED);
      }
    });
    myAdditionalOptionsField.setDialogCaption(myFieldLabel.getText());
  }

  private void setOptionsEnabled(final boolean selected) {
    myCbGenerateIiopStubs.setEnabled(selected);
    myCbGenerateNoWarnings.setEnabled(selected);
    myCbDebuggingInfo.setEnabled(selected);
    myFieldLabel.setEnabled(selected);
    myAdditionalOptionsField.setEnabled(selected);
  }

  public String getDisplayName() {
    return CompilerBundle.message("rmi.compiler.description");
  }

  public Icon getIcon() {
    return null;
  }

  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    boolean isModified = false;
    isModified |= ComparingUtils.isModified(myCbEnabled, myRmicSettings.IS_EANABLED);
    isModified |= ComparingUtils.isModified(myCbGenerateIiopStubs, myRmicSettings.GENERATE_IIOP_STUBS);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myRmicSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myRmicSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myRmicSettings.ADDITIONAL_OPTIONS_STRING);
    return isModified;
  }

  public void apply() throws ConfigurationException {
    myRmicSettings.IS_EANABLED =  myCbEnabled.isSelected();
    myRmicSettings.GENERATE_IIOP_STUBS =  myCbGenerateIiopStubs.isSelected();
    myRmicSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myRmicSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myRmicSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
  }

  public void reset() {
    myCbEnabled.setSelected(myRmicSettings.IS_EANABLED);
    setOptionsEnabled(myRmicSettings.IS_EANABLED);
    myCbGenerateIiopStubs.setSelected(myRmicSettings.GENERATE_IIOP_STUBS);
    myCbDebuggingInfo.setSelected(myRmicSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myRmicSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myRmicSettings.ADDITIONAL_OPTIONS_STRING);
  }

  public void disposeUIResources() {
  }

}
