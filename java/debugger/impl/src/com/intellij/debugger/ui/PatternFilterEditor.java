// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.IconUtil;

import javax.swing.*;

public class PatternFilterEditor extends ClassFilterEditor {
  public PatternFilterEditor(Project project) {
    super(project);
    getEmptyText().setText(DebuggerBundle.message("filters.not.configured"));
  }

  protected void addClassFilter() {
    addPatternFilter();
  }

  protected String getAddButtonText() {
    return DebuggerBundle.message("button.add");
  }

  @Override
  protected Icon getAddButtonIcon() {
    return IconUtil.getAddIcon();
  }

  @Override
  protected boolean addPatternButtonVisible() {
    return false;
  }
}
