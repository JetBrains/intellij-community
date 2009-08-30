package com.intellij.compiler.impl.javaCompiler.eclipse;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.compiler.CompilerBundle;
import com.intellij.ui.RawCommandLineEditor;
import com.intellij.compiler.options.ComparingUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author cdr
 */
public class EclipseCompilerConfigurable implements Configurable {
  private JPanel myPanel;
  private JCheckBox myCbDeprecation;
  private JCheckBox myCbDebuggingInfo;
  private JCheckBox myCbGenerateNoWarnings;
  private RawCommandLineEditor myAdditionalOptionsField;
  private JTextField myJavacMaximumHeapField;
  private final EclipseCompilerSettings myCompilerSettings;

  public EclipseCompilerConfigurable(EclipseCompilerSettings compilerSettings) {
    myCompilerSettings = compilerSettings;
    myAdditionalOptionsField.setDialogCaption(CompilerBundle.message("java.compiler.option.additional.command.line.parameters"));
  }

  public String getDisplayName() {
    return null;
  }

  public Icon getIcon() {
    return null;
  }

  @Nullable
  @NonNls
  public String getHelpTopic() {
    return null;
  }

  public JComponent createComponent() {
    return myPanel;
  }

  public boolean isModified() {
    boolean isModified = ComparingUtils.isModified(myJavacMaximumHeapField, myCompilerSettings.MAXIMUM_HEAP_SIZE);

    isModified |= ComparingUtils.isModified(myCbDeprecation, myCompilerSettings.DEPRECATION);
    isModified |= ComparingUtils.isModified(myCbDebuggingInfo, myCompilerSettings.DEBUGGING_INFO);
    isModified |= ComparingUtils.isModified(myCbGenerateNoWarnings, myCompilerSettings.GENERATE_NO_WARNINGS);
    isModified |= ComparingUtils.isModified(myAdditionalOptionsField, myCompilerSettings.ADDITIONAL_OPTIONS_STRING);
    return isModified;
  }

  public void apply() throws ConfigurationException {

    try {
      myCompilerSettings.MAXIMUM_HEAP_SIZE = Integer.parseInt(myJavacMaximumHeapField.getText());
      if(myCompilerSettings.MAXIMUM_HEAP_SIZE < 1) {
        myCompilerSettings.MAXIMUM_HEAP_SIZE = 128;
      }
    }
    catch(NumberFormatException exception) {
      myCompilerSettings.MAXIMUM_HEAP_SIZE = 128;
    }

    myCompilerSettings.DEPRECATION =  myCbDeprecation.isSelected();
    myCompilerSettings.DEBUGGING_INFO = myCbDebuggingInfo.isSelected();
    myCompilerSettings.GENERATE_NO_WARNINGS = myCbGenerateNoWarnings.isSelected();
    myCompilerSettings.ADDITIONAL_OPTIONS_STRING = myAdditionalOptionsField.getText();
  }

  public void reset() {
    myJavacMaximumHeapField.setText(Integer.toString(myCompilerSettings.MAXIMUM_HEAP_SIZE));
    myCbDeprecation.setSelected(myCompilerSettings.DEPRECATION);
    myCbDebuggingInfo.setSelected(myCompilerSettings.DEBUGGING_INFO);
    myCbGenerateNoWarnings.setSelected(myCompilerSettings.GENERATE_NO_WARNINGS);
    myAdditionalOptionsField.setText(myCompilerSettings.ADDITIONAL_OPTIONS_STRING);
  }

  public void disposeUIResources() {

  }
}
