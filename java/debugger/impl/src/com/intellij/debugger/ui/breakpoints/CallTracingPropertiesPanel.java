// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.settings.TraceSettings;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaLineBreakpointProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CallTracingPropertiesPanel extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaLineBreakpointProperties>> {
  private final Project myProject;
  private JBCheckBox myStartTracing;
  private JBCheckBox myEndTracing;

  public CallTracingPropertiesPanel(Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    myStartTracing = new JBCheckBox("Start");
    myEndTracing = new JBCheckBox("Stop");
    JButton filters = new JButton("Filters...");
    //DialogUtil.registerMnemonic(myStartTracing);
    //DialogUtil.registerMnemonic(myEndTracing);

    JPanel _panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
    _panel.add(myStartTracing);
    _panel.add(myEndTracing);
    _panel.add(filters);
    _panel.setBorder(IdeBorderFactory.createTitledBorder("Tracing", true));

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox toUncheck = null;
        if (myStartTracing.isSelected() && myEndTracing.isSelected()) {
          Object source = e.getSource();
          if (myStartTracing.equals(source)) {
            toUncheck = myEndTracing;
          }
          else if (myEndTracing.equals(source)) {
            toUncheck = myStartTracing;
          }
          if (toUncheck != null) {
            toUncheck.setSelected(false);
          }
        }
      }
    };
    myStartTracing.addActionListener(listener);
    myEndTracing.addActionListener(listener);

    filters.addActionListener(e -> {
      EditClassFiltersDialog dialog = new EditClassFiltersDialog(myProject);
      TraceSettings traceSettings = TraceSettings.getInstance();
      dialog.setFilters(traceSettings.getClassFilters(), traceSettings.getClassExclusionFilters());
      dialog.setTitle("Tracing Class Filters");
      dialog.show();
      if (dialog.getExitCode() == DialogWrapper.OK_EXIT_CODE) {
        traceSettings.setClassFilters(dialog.getFilters());
        traceSettings.setClassExclusionFilters(dialog.getExclusionFilters());
      }
    });

    return _panel;
  }

  @Override
  public void loadFrom(@NotNull XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    myStartTracing.setSelected(breakpoint.getProperties().isTRACING_START());
    myEndTracing.setSelected(breakpoint.getProperties().isTRACING_END());
  }

  @Override
  public void saveTo(@NotNull XLineBreakpoint<JavaLineBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().setTRACING_START(myStartTracing.isSelected());
    changed = breakpoint.getProperties().setTRACING_END(myEndTracing.isSelected()) || changed;
    if (changed) {
      ((XBreakpointBase)breakpoint).fireBreakpointChanged();
    }
  }
}