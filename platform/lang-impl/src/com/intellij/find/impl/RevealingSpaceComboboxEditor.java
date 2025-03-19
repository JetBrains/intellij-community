// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.find.impl;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.editor.Editor;
import com.intellij.ui.StringComboboxEditor;
import org.jetbrains.annotations.ApiStatus;

import javax.swing.*;

@ApiStatus.Internal
public final class RevealingSpaceComboboxEditor extends StringComboboxEditor {
  public RevealingSpaceComboboxEditor(final Project project, ComboBox comboBox) {
    super(project, FileTypes.PLAIN_TEXT, comboBox);

    SwingUtilities.invokeLater(() -> {
      Editor editor = getEditor();
      if (editor != null) {
        editor.getSettings().setWhitespacesShown(true);
      }
    });
  }

  @Override
  public void setItem(Object anObject) {
    super.setItem(anObject);
    Editor editor = getEditor();
    if (editor != null) {
      editor.getSettings().setWhitespacesShown(true);
    }
  }
}