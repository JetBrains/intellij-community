// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport;

import com.intellij.ide.JavaUiBundle;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.components.StorageScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ProjectFormatPanel {
  private static final String STORAGE_FORMAT_PROPERTY = "default.storage.format";

  private JComboBox<String> myStorageFormatCombo;
  private JPanel myWholePanel;

  public ProjectFormatPanel() {
    myStorageFormatCombo.insertItemAt(StorageFormat.DIR_BASED.id(), 0);
    myStorageFormatCombo.insertItemAt(StorageFormat.FILE_BASED.id(), 1);

    final PropertiesComponent instance = PropertiesComponent.getInstance();
    final String savedValue = instance.getValue(STORAGE_FORMAT_PROPERTY, StorageFormat.DIR_BASED.id());
    myStorageFormatCombo.setSelectedItem(savedValue);

    final SimpleListCellRenderer<String> renderer = SimpleListCellRenderer.create(StorageFormat.FILE_BASED.id(),
                                                                                  s -> StorageFormat.of(s).getTitle());
    myStorageFormatCombo.setRenderer(renderer);
  }

  public JPanel getPanel() {
    return myWholePanel;
  }

  @NotNull
  public JComboBox<String> getStorageFormatComboBox() {
    return myStorageFormatCombo;
  }

  public void updateData(@NotNull WizardContext context) {
    StorageScheme format = isDefault() ? StorageScheme.DEFAULT : StorageScheme.DIRECTORY_BASED;
    context.setProjectStorageFormat(format);
    final StorageFormat storageFormat = StorageFormat.of(format);
    PropertiesComponent.getInstance().setValue(STORAGE_FORMAT_PROPERTY, storageFormat.id(), StorageFormat.DIR_BASED.id());
  }

  public void setVisible(boolean visible) {
    myWholePanel.setVisible(visible);
  }

  public boolean isDefault() {
    return StorageFormat.FILE_BASED.id().equals(myStorageFormatCombo.getSelectedItem());
  }

  private enum StorageFormat {
    DIR_BASED(0), FILE_BASED(1);

    private final int myId;

    StorageFormat(int id) {
      myId = id;
    }

    private @NlsSafe String id() {
      return Integer.toString(myId);
    }

    private static @NotNull StorageFormat of(@NotNull final String id) {
      if (id.equals(DIR_BASED.id())) return DIR_BASED;
      return FILE_BASED;
    }

    private static @NotNull StorageFormat of(@NotNull final StorageScheme id) {
      switch (id) {
        case DIRECTORY_BASED: return DIR_BASED;
        case DEFAULT: return FILE_BASED;
        default:
          throw new IllegalStateException("Unexpected value: " + id);
      }
    }

    private @NlsContexts.Label String getTitle() {
      if (myId == 0) return JavaUiBundle.message("label.directory.based", Project.DIRECTORY_STORE_FOLDER);
      return JavaUiBundle.message("label.ipr.file.based");
    }
  }
}