/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * User: anna
 * Date: 09-Feb-2009
 */
package com.intellij.projectImport;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.StorageScheme;

import javax.swing.*;

public class ProjectFormatPanel {
  public static final String DIR_BASED = ".idea (directory based)";
  private static final String FILE_BASED = ".ipr (file based)";
  private JComboBox myStorageFormatCombo;
  private JPanel myWholePanel;

  public static final String STORAGE_FORMAT_PROPERTY = "default.storage.format";

  public ProjectFormatPanel() {
    myStorageFormatCombo.insertItemAt(DIR_BASED, 0);
    myStorageFormatCombo.insertItemAt(FILE_BASED, 1);
    myStorageFormatCombo.setSelectedItem(PropertiesComponent.getInstance().getOrInit(STORAGE_FORMAT_PROPERTY, DIR_BASED));
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
    PropertiesComponent.getInstance().setValue(STORAGE_FORMAT_PROPERTY, isDefault() ? FILE_BASED : DIR_BASED);
  }

  public void setVisible(boolean visible) {
    myWholePanel.setVisible(visible);
  }

  public boolean isDefault() {
    return FILE_BASED.equals(myStorageFormatCombo.getSelectedItem());
  }
}