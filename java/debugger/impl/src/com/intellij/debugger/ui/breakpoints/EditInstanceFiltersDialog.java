// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.ui.InstanceFilterEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.util.Arrays;

public class EditInstanceFiltersDialog extends DialogWrapper {
  private InstanceFilterEditor myInstanceFilterEditor;
  private final Project myProject;

  public EditInstanceFiltersDialog(Project project) {
    super(project, true);
    myProject = project;
    setTitle(JavaDebuggerBundle.message("instance.filters.dialog.title"));
    init();
  }

  @Override
  protected JComponent createCenterPanel() {
    myInstanceFilterEditor = new InstanceFilterEditor(myProject);
    myInstanceFilterEditor.setPreferredSize(JBUI.size(400, 200));
    return myInstanceFilterEditor;
  }

  @Override
  public void dispose() {
    myInstanceFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(InstanceFilter[] filters) {
    ClassFilter[] cFilters = InstanceFilter.createClassFilters(filters);
    myInstanceFilterEditor.setFilters(cFilters);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "#com.intellij.debugger.ui.breakpoints.EditInstanceFiltersDialog";
  }

  public InstanceFilter[] getFilters() {
    return Arrays.stream(myInstanceFilterEditor.getFilters()).map(InstanceFilter::create).toArray(InstanceFilter[]::new);
  }
}
