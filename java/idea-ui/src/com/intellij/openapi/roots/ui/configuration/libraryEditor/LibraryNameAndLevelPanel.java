/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui.configuration.libraryEditor;

import com.intellij.ui.ListCellRendererWrapper;
import com.intellij.openapi.project.ProjectBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class LibraryNameAndLevelPanel {
  private JTextField myLibraryNameField;
  private JComboBox myLevelComboBox;
  private String myDefaultLibraryName;

  public LibraryNameAndLevelPanel(@NotNull FormBuilder formBuilder, @NotNull String libraryName, @Nullable LibrariesContainer.LibraryLevel level) {
    this(formBuilder, libraryName, Arrays.asList(LibrariesContainer.LibraryLevel.values()), level);
  }

  public LibraryNameAndLevelPanel(@NotNull FormBuilder formBuilder, @NotNull String libraryName, @NotNull List<LibrariesContainer.LibraryLevel> availableLevels,
                                  @Nullable LibrariesContainer.LibraryLevel level) {
    myLibraryNameField = new JTextField(25);
    formBuilder.addLabeledComponent("&Name:", myLibraryNameField);
    myLibraryNameField.setText(libraryName);
    myLevelComboBox = new JComboBox();
    if (level != null && !availableLevels.isEmpty()) {
      formBuilder.addLabeledComponent("&Level:", myLevelComboBox);
      final Map<LibrariesContainer.LibraryLevel, String> levels = new HashMap<>();
      levels.put(LibrariesContainer.LibraryLevel.GLOBAL, ProjectBundle.message("combobox.item.global.library"));
      levels.put(LibrariesContainer.LibraryLevel.PROJECT, ProjectBundle.message("combobox.item.project.library"));
      levels.put(LibrariesContainer.LibraryLevel.MODULE, ProjectBundle.message("combobox.item.module.library"));
      myLevelComboBox.setRenderer(new ListCellRendererWrapper() {
        @Override
        public void customize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
          if (value instanceof LibrariesContainer.LibraryLevel) {
            final LibrariesContainer.LibraryLevel level = (LibrariesContainer.LibraryLevel)value;
            setText(levels.get(level));
          }
        }
      });
      myLevelComboBox.setModel(new CollectionComboBoxModel(availableLevels, level));
    }
  }

  public String getLibraryName() {
    return myLibraryNameField.getText();
  }

  public LibrariesContainer.LibraryLevel getLibraryLevel() {
    return (LibrariesContainer.LibraryLevel)myLevelComboBox.getSelectedItem();
  }

  public JTextField getLibraryNameField() {
    return myLibraryNameField;
  }

  public JComboBox getLevelComboBox() {
    return myLevelComboBox;
  }

  public void setDefaultName(@NotNull String defaultLibraryName) {
    if (myDefaultLibraryName != null && myDefaultLibraryName.equals(getLibraryName())) {
      myLibraryNameField.setText(defaultLibraryName);
    }
    myDefaultLibraryName = defaultLibraryName;
  }

  public static FormBuilder createFormBuilder() {
    return FormBuilder.createFormBuilder();
  }
}
