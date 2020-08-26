// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectFormatPanel {
  private static final String STORAGE_FORMAT_PROPERTY = "default.storage.format";
  public static final String PROPERTY_COMPONENT_DIR_BASED = Project.DIRECTORY_STORE_FOLDER + " (directory based)";
  private static final String PROPERTY_COMPONENT_FILE_BASED = ".ipr (file based)";

  private JComboBox<String> myStorageFormatCombo;
  private JPanel myWholePanel;

  public ProjectFormatPanel() {
    myStorageFormatCombo.insertItemAt(JavaUiBundle.message("label.directory.based", Project.DIRECTORY_STORE_FOLDER), 0);
    myStorageFormatCombo.insertItemAt(JavaUiBundle.message("label.ipr.file.based"), 1);
    myStorageFormatCombo.setSelectedItem(PropertiesComponent.getInstance().getValue(STORAGE_FORMAT_PROPERTY, PROPERTY_COMPONENT_DIR_BASED));
  }

  public JPanel getPanel() {
    return myWholePanel;
  }

  @NotNull
  public JComboBox<String> getStorageFormatComboBox() {
    return myStorageFormatCombo;
  }

  public void updateData(@NotNull WizardContext context) {
    final String fileBased = JavaUiBundle.message("label.ipr.file.based");
    StorageScheme format = fileBased.equals(myStorageFormatCombo.getSelectedItem()) ? StorageScheme.DEFAULT : StorageScheme.DIRECTORY_BASED;
    context.setProjectStorageFormat(format);
    PropertiesComponent.getInstance().setValue(STORAGE_FORMAT_PROPERTY, isDefault() ? PROPERTY_COMPONENT_FILE_BASED
                                                                                    : PROPERTY_COMPONENT_DIR_BASED,
                                               PROPERTY_COMPONENT_DIR_BASED);
  }

  public void setVisible(boolean visible) {
    myWholePanel.setVisible(visible);
  }

  public boolean isDefault() {
    final String fileBased = JavaUiBundle.message("label.ipr.file.based");
    return fileBased.equals(myStorageFormatCombo.getSelectedItem());
  }
}