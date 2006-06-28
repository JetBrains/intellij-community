package com.intellij.debugger.ui.breakpoints;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.debugger.InstanceFilter;
import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.ui.InstanceFilterEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;

import javax.swing.*;
import java.awt.*;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:44:38 PM
 */
public class EditInstanceFiltersDialog extends DialogWrapper{
  private InstanceFilterEditor myInstanceFilterEditor;
  private Project myProject;

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
    myInstanceFilterEditor.setPreferredSize(new Dimension(400, 200));
    myInstanceFilterEditor.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("instance.filters.dialog.instance.filters.group")));
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
    ClassFilter [] cFilters = myInstanceFilterEditor.getFilters();
    InstanceFilter [] ifilters = new InstanceFilter[cFilters.length];
    for (int i = 0; i < ifilters.length; i++) {
      ifilters[i] = InstanceFilter.create(cFilters[i]);
    }
    return ifilters;
  }
}
