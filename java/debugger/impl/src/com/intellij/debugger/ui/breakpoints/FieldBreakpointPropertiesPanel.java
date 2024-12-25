// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBBox;
import com.intellij.util.ui.DialogUtil;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaFieldBreakpointProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class FieldBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaFieldBreakpointProperties>> {
  private JCheckBox myWatchAccessCheckBox;
  private JCheckBox myWatchModificationCheckBox;

  //public FieldBreakpointPropertiesPanel(final Project project, boolean compact) {
  //  super(project, FieldBreakpoint.CATEGORY, compact);
  //}


  @Override
  public @NotNull JComponent getComponent() {
    JPanel _panel;
    JPanel _panel0;
    myWatchAccessCheckBox = new JCheckBox(JavaDebuggerBundle.message("label.field.breakpoint.properties.panel.field.access"));
    myWatchModificationCheckBox = new JCheckBox(JavaDebuggerBundle.message("label.field.breakpoint.properties.panel.field.modification"));
    DialogUtil.registerMnemonic(myWatchAccessCheckBox);
    DialogUtil.registerMnemonic(myWatchModificationCheckBox);


    JBBox watchBox = JBBox.createVerticalBox();
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchAccessCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchModificationCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);

    _panel = new JPanel(new BorderLayout());
    _panel0 = new JPanel(new BorderLayout());
    _panel0.add(watchBox, BorderLayout.CENTER);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    _panel.add(_panel0, BorderLayout.NORTH);
    _panel.setBorder(IdeBorderFactory.createTitledBorder(JavaDebuggerBundle.message("label.group.watch.events")));

    ActionListener listener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        JCheckBox toCheck = null;
        if (!myWatchAccessCheckBox.isSelected() && !myWatchModificationCheckBox.isSelected()) {
          Object source = e.getSource();
          if (myWatchAccessCheckBox.equals(source)) {
            toCheck = myWatchModificationCheckBox;
          }
          else if (myWatchModificationCheckBox.equals(source)) {
            toCheck = myWatchAccessCheckBox;
          }
          if (toCheck != null) {
            toCheck.setSelected(true);
          }
        }
      }
    };
    myWatchAccessCheckBox.addActionListener(listener);
    myWatchModificationCheckBox.addActionListener(listener);

    return _panel;
  }

  @Override
  public void loadFrom(@NotNull XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    myWatchAccessCheckBox.setSelected(breakpoint.getProperties().WATCH_ACCESS);
    myWatchModificationCheckBox.setSelected(breakpoint.getProperties().WATCH_MODIFICATION);
  }

  @Override
  public void saveTo(@NotNull XLineBreakpoint<JavaFieldBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().WATCH_ACCESS != myWatchAccessCheckBox.isSelected();
    breakpoint.getProperties().WATCH_ACCESS = myWatchAccessCheckBox.isSelected();
    changed = breakpoint.getProperties().WATCH_MODIFICATION != myWatchModificationCheckBox.isSelected() || changed;
    breakpoint.getProperties().WATCH_MODIFICATION = myWatchModificationCheckBox.isSelected();
    if (changed) {
      ((XBreakpointBase<?, ?, ?>)breakpoint).fireBreakpointChanged();
    }
  }
}