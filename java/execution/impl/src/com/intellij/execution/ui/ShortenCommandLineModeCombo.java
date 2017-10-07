// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.ui;

import com.intellij.execution.ShortenCommandLine;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class ShortenCommandLineModeCombo extends ComboBox<ShortenCommandLine> {
  private final Project myProject;

  public ShortenCommandLineModeCombo(Project project, JrePathEditor pathEditor) {
    myProject = project;
    initModel(null, pathEditor);
    setRenderer(new ColoredListCellRenderer<ShortenCommandLine>() {
      @Override
      protected void customizeCellRenderer(@NotNull JList<? extends ShortenCommandLine> list,
                                           ShortenCommandLine value,
                                           int index,
                                           boolean selected,
                                           boolean hasFocus) {
        if (value == null) {
          ShortenCommandLine defaultMode = ShortenCommandLine.getDefaultMethod(myProject, getJdkRoot(pathEditor));
          append("user-local default: " + defaultMode.getPresentableName()).append(" - " + defaultMode.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
        else {
          append(value.getPresentableName()).append(" - " + value.getDescription(), SimpleTextAttributes.GRAYED_ATTRIBUTES);
        }
      }
    });
    pathEditor.addActionListener(e -> {
      Object item = getSelectedItem();
      initModel((ShortenCommandLine)item, pathEditor);
    });
  }

  private void initModel(ShortenCommandLine preselection, JrePathEditor pathEditor) {
    removeAllItems();

    String jdkRoot = getJdkRoot(pathEditor);
    addItem(null);
    for (ShortenCommandLine mode : ShortenCommandLine.values()) {
      if (mode.isApplicable(jdkRoot)) {
        addItem(mode);
      }
    }

    setSelectedItem(preselection);
  }

  @Nullable
  private static String getJdkRoot(JrePathEditor pathEditor) {
    String jrePathOrName = pathEditor.getJrePathOrName();
    if (jrePathOrName != null) {
      Sdk configuredJdk = ProjectJdkTable.getInstance().findJdk(jrePathOrName);
      if (configuredJdk != null) {
        return configuredJdk.getHomePath();
      }
      else {
        return jrePathOrName;
      }
    }
    return null;
  }
}
