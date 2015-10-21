/*
 * Copyright 2000-2015 JetBrains s.r.o.
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


  protected JComponent createCenterPanel() {
    JPanel contentPanel = new JPanel(new BorderLayout());

    Box mainPanel = Box.createHorizontalBox();

    myClassFilterEditor = new ClassFilterEditor(myProject, myChooserFilter, "reference.viewBreakpoints.classFilters.newPattern");
    myClassFilterEditor.setPreferredSize(JBUI.size(400, 200));
    myClassFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.inclusion.filters.group"), false));
    mainPanel.add(myClassFilterEditor);

    myClassExclusionFilterEditor = new ClassFilterEditor(myProject, myChooserFilter, "reference.viewBreakpoints.classFilters.newPattern");
    myClassExclusionFilterEditor.setPreferredSize(JBUI.size(400, 200));
    myClassExclusionFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("class.filters.dialog.exclusion.filters.group"), false));
    mainPanel.add(myClassExclusionFilterEditor);

    contentPanel.add(mainPanel, BorderLayout.CENTER);

    return contentPanel;
  }

  public void dispose(){
    myClassFilterEditor.stopEditing();
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