/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.ui.InstanceFilterEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.util.Arrays;

public class EditInstanceFiltersDialog extends DialogWrapper{
  private InstanceFilterEditor myInstanceFilterEditor;
  private final Project myProject;

  public EditInstanceFiltersDialog (Project project) {
    super(project, true);
    myProject = project;
    setTitle(DebuggerBundle.message("instance.filters.dialog.title"));
    init();
  }

  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myInstanceFilterEditor = new InstanceFilterEditor(myProject);
    myInstanceFilterEditor.setPreferredSize(JBUI.size(400, 200));
    myInstanceFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("instance.filters.dialog.instance.filters.group"), false));
    mainPanel.add(myInstanceFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  public void dispose(){
    myInstanceFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(InstanceFilter[] filters) {
    ClassFilter[] cFilters = InstanceFilter.createClassFilters(filters);
    myInstanceFilterEditor.setFilters(cFilters);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.EditInstanceFiltersDialog";
  }

  public InstanceFilter[] getFilters() {
    return Arrays.stream(myInstanceFilterEditor.getFilters()).map(InstanceFilter::create).toArray(InstanceFilter[]::new);
  }
}
