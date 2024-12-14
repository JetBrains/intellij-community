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

import com.intellij.ide.JavaUiBundle;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
public class LibraryNameAndLevelPanel {
  private final JTextField myLibraryNameField;
  private final JComboBox<LibrariesContainer.LibraryLevel> myLevelComboBox;
  private String myDefaultLibraryName;

  public LibraryNameAndLevelPanel(@NotNull FormBuilder formBuilder, @NotNull String libraryName, @Nullable LibrariesContainer.LibraryLevel level) {
    this(formBuilder, libraryName, new ArrayList<>(Arrays.asList(LibrariesContainer.LibraryLevel.values())), level);
  }

  @Contract(mutates = "param3")
  public LibraryNameAndLevelPanel(@NotNull FormBuilder formBuilder, @NotNull String libraryName, @NotNull List<LibrariesContainer.LibraryLevel> availableLevels,
                                  @Nullable LibrariesContainer.LibraryLevel level) {
    myLibraryNameField = new JTextField(25);
    formBuilder.addLabeledComponent(JavaUiBundle.message("label.library.name"), myLibraryNameField);
    myLibraryNameField.setText(libraryName);
    myLevelComboBox = new ComboBox<>();
    if (level != null && !availableLevels.isEmpty()) {
      formBuilder.addLabeledComponent(JavaUiBundle.message("label.library.level"), myLevelComboBox);
      final Map<LibrariesContainer.LibraryLevel, String> levels = new HashMap<>();
      levels.put(LibrariesContainer.LibraryLevel.GLOBAL, JavaUiBundle.message("combobox.item.global.library"));
      levels.put(LibrariesContainer.LibraryLevel.PROJECT, JavaUiBundle.message("combobox.item.project.library"));
      levels.put(LibrariesContainer.LibraryLevel.MODULE, JavaUiBundle.message("combobox.item.module.library"));
      myLevelComboBox.setRenderer(SimpleListCellRenderer.create("", levels::get));
      myLevelComboBox.setModel(new CollectionComboBoxModel<>(availableLevels, level));
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
