// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * Class EditClassFiltersDialog
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ide.util.ClassFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;

public class EditClassFiltersDialog extends DialogWrapper {
  private ClassFilterEditor myClassFilterEditor;
  private ClassFilterEditor myClassExclusionFilterEditor;
  private final Project myProject;
  private final ClassFilter myChooserFilter;

  public EditClassFiltersDialog(Project project) {
    this(project, null);
  }

  public EditClassFiltersDialog(Project project, ClassFilter filter) {
    super(project, true);
    myChooserFilter = filter;
    myProject = project;
    setTitle(DebuggerBundle.message("class.filters.dialog.title"));
    init();
  }

  protected ClassFilterEditor createClassFilterEditor(Project project) {
    return new ClassFilterEditor(project, myChooserFilter, "reference.viewBreakpoints.classFilters.newPattern");
  }

  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myClassFilterEditor = createClassFilterEditor(myProject);
    myClassFilterEditor.setPreferredSize(JBUI.size(400, 200));
    myClassFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.inclusion.filters.group"), false));
    mainPanel.add(myClassFilterEditor);

    myClassExclusionFilterEditor = createClassFilterEditor(myProject);
    myClassExclusionFilterEditor.setPreferredSize(JBUI.size(400, 200));
    myClassExclusionFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.exclusion.filters.group"), false));
    mainPanel.add(myClassExclusionFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  public void dispose(){
    myClassFilterEditor.stopEditing();
    myClassExclusionFilterEditor.stopEditing();
    super.dispose();
  }

  public void setFilters(com.intellij.ui.classFilter.ClassFilter[] filters, com.intellij.ui.classFilter.ClassFilter[] inverseFilters) {
    myClassFilterEditor.setFilters(filters);
    myClassExclusionFilterEditor.setFilters(inverseFilters);
  }

  protected String getDimensionServiceKey(){
    return "#com.intellij.debugger.ui.breakpoints.EditClassFiltersDialog";
  }

  public com.intellij.ui.classFilter.ClassFilter[] getFilters() {
    return myClassFilterEditor.getFilters();
  }

  public com.intellij.ui.classFilter.ClassFilter[] getExclusionFilters() {
    return myClassExclusionFilterEditor.getFilters();
  }

  protected String getHelpId() {
    return "reference.viewBreakpoints.classFilters";
  }
}