package org.jetbrains.jps;

/**
 * @author nik
 */
public class UiDesignerConfiguration {
  private boolean myCopyFormsRuntimeToOutput = true;

  public boolean isCopyFormsRuntimeToOutput() {
    return myCopyFormsRuntimeToOutput;
  }

  public void setCopyFormsRuntimeToOutput(boolean copyFormsRuntimeToOutput) {
    myCopyFormsRuntimeToOutput = copyFormsRuntimeToOutput;
  }
}
