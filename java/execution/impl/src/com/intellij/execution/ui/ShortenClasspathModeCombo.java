// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ShortenClasspath;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ShortenClasspathModeCombo extends ComboBox<ShortenClasspath> {
  private final Project myProject;

  public ShortenClasspathModeCombo(Project project) {
    myProject = project;
    addItem(null);
    for (ShortenClasspath mode : ShortenClasspath.values()) {
      addItem(mode);
    }
    setRenderer(new ColoredListCellRenderer<ShortenClasspath>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ShortenClasspath> list,
                                           ShortenClasspath value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          ShortenClasspath defaultMode = ShortenClasspath.getDefaultMethod(myProject);
          append("user-local default: " + defaultMode.getPresentableName()).append(" - " + defaultMode.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value.getPresentableName()).append(" - " + value.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    });
  }
}
