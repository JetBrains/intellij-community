// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.IconUtil;

import javax.swing.*;

public class PatternFilterEditor extends ClassFilterEditor {
  public PatternFilterEditor(Project project) {
    super(project);
    getEmptyText().setText(JavaDebuggerBundle.message("filters.not.configured"));
  }

  @Override
  protected void addClassFilter() {
    addPatternFilter();
  }

  @Override
  protected String getAddButtonText() {
    return JavaDebuggerBundle.message("button.add");
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
