package com.intellij.compiler.impl.javaCompiler.jikes;

import com.intellij.compiler.options.ComparingUtils;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.RawCommandLineEditor;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

/**
 * @author Eugene Zhuravlev
 *         Date: Mar 30, 2004
 */
public class JikesConfigurable implements Configurable{
  private JPanel myPanel;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbDeprecation;
  private JCheckBox myCbGenerateNoWarnings ;
  private RawCommandLineEditor myAdditionalOptionsField;
  private JTextField myPathField;
  private JButton myJikesPathFieldBrowseButton;
  private final JikesSettings myJikesSettings;

  public JikesConfigurable(JikesSettings jikesSettings) {
    myJikesSettings = jikesSettings;
    myJikesPathFieldBrowseButton.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        FileChooserDescriptor descriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor();
        VirtualFile[] files = FileChooser.chooseFiles(myPathField, descriptor);
        if (files.length != 0) {
          myPathField.setText(files[0].getPath().replace('/', File.separatorChar));
        }
      }
    });
    myAdditionalOptionsField.setDialogCaption(CompilerBundle.message("java.compiler.option.additional.command.line.parameters"));
  }

  public String getDisplayName() {
    return null;
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
    isModified |= ComparingUtils.isModified(myPathField, myJikesSettings.JIKES_PATH.replace('/', File.separatorChar));
    isModified |= ComparingUtils.isModified(myCbDeprecation, myJikesSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myJikesSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myJikesSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myJikesSettings.ADDITIONAL_OPTIONS_STRING);
    return isModified;
  }

  public void apply() throws ConfigurationException {
    myJikesSettings.JIKES_PATH = myPathField.getText().trim().replace(File.separatorChar, '/');
    myJikesSettings.DEPRECATION = myCbDeprecation.isSelected();
    myJikesSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myJikesSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myJikesSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
  }

  public void reset() {
    myPathField.setText(myJikesSettings.JIKES_PATH.replace('/', File.separatorChar));
    myCbDeprecation.setSelected(myJikesSettings.DEPRECATION);
    myCbDebuggingInfo.setSelected(myJikesSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myJikesSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myJikesSettings.ADDITIONAL_OPTIONS_STRING);
  }

  public void disposeUIResources() {
  }
}
