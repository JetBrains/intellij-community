/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.application.options.colors.fileStatus;

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.ui.table.JBTable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class FileStatusColorsPanel {
  private JPanel myTopPanel;
  private JBTable myFileStatusColorsTable;
  private final FileStatusColorsTableModel myModel;

  public FileStatusColorsPanel(@NotNull FileStatus[] fileStatuses) {
    myModel = new FileStatusColorsTableModel(fileStatuses, getCurrentScheme());
    myFileStatusColorsTable.setModel(
      myModel);
    ((FileStatusColorsTable)myFileStatusColorsTable).adjustColumnWidths();
    myModel.addTableModelListener(myFileStatusColorsTable);
  }

  public JPanel getComponent() {
    return myTopPanel;
  }

  private void createUIComponents() {
    myFileStatusColorsTable = new FileStatusColorsTable(getCurrentScheme().getDefaultForeground());
  }

  @NotNull
  private static EditorColorsScheme getCurrentScheme() {
    return EditorColorsManager.getInstance().getSchemeForCurrentUITheme();
  }

  @NotNull
  public FileStatusColorsTableModel getModel() {
    return myModel;
  }
}
