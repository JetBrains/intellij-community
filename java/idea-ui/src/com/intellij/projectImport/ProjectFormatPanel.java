/*
 * User: anna
 * Date: 09-Feb-2009
 */
package com.intellij.projectImport;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.StorageScheme;

import javax.swing.*;

public class ProjectFormatPanel {
  private static final String DIR_BASED = ".idea (directory based)";
  private static final String FILE_BASED = ".ipr (file based)";
  private JComboBox myStorageFormatCombo;
  private JPanel myWholePanel;

  public ProjectFormatPanel() {
    myStorageFormatCombo.insertItemAt(DIR_BASED, 0);
    myStorageFormatCombo.insertItemAt(FILE_BASED, 1);
    myStorageFormatCombo.setSelectedItem(DIR_BASED);
  }


  public JPanel getPanel() {
    return myWholePanel;
  }

  public JComboBox getStorageFormatComboBox() {
    return myStorageFormatCombo;
  }

  public void updateData(WizardContext context) {
    context.setProjectStorageFormat(
      FILE_BASED.equals(myStorageFormatCombo.getSelectedItem()) ? StorageScheme.DEFAULT : StorageScheme.DIRECTORY_BASED);
  }
}