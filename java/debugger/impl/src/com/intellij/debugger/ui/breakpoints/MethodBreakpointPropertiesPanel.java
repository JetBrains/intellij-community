// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBBox;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.xdebugger.breakpoints.XBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import com.intellij.xdebugger.impl.breakpoints.XBreakpointBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.debugger.breakpoints.properties.JavaMethodBreakpointProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MethodBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XBreakpoint<JavaMethodBreakpointProperties>> {
  private JCheckBox myEmulatedCheckBox;
  private JCheckBox myWatchEntryCheckBox;
  private JCheckBox myWatchExitCheckBox;

  //public MethodBreakpointPropertiesPanel(final Project project, boolean compact) {
  //  super(project, MethodBreakpoint.CATEGORY, compact);
  //}


  @Override
  public @NotNull JComponent getComponent() {
    JPanel _panel, _panel0;

    myEmulatedCheckBox = new JCheckBox(JavaDebuggerBundle.message("label.method.breakpoint.properties.panel.emulated"));
    myWatchEntryCheckBox = new JCheckBox(JavaDebuggerBundle.message("label.method.breakpoint.properties.panel.method.entry"));
    myWatchExitCheckBox = new JCheckBox(JavaDebuggerBundle.message("label.method.breakpoint.properties.panel.method.exit"));
    DialogUtil.registerMnemonic(myWatchEntryCheckBox);
    DialogUtil.registerMnemonic(myWatchExitCheckBox);


    JBBox watchBox = JBBox.createVerticalBox();
    _panel = JBUI.Panels.simplePanel();
    _panel.add(myEmulatedCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchEntryCheckBox, BorderLayout.NORTH);
    watchBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myWatchExitCheckBox, BorderLayout.NORTH);
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
        if (!myWatchEntryCheckBox.isSelected() && !myWatchExitCheckBox.isSelected()) {
          Object source = e.getSource();
          if (myWatchEntryCheckBox.equals(source)) {
            toCheck = myWatchExitCheckBox;
          }
          else if (myWatchExitCheckBox.equals(source)) {
            toCheck = myWatchEntryCheckBox;
          }
          if (toCheck != null) {
            toCheck.setSelected(true);
          }
        }
      }
    };
    myWatchEntryCheckBox.addActionListener(listener);
    myWatchExitCheckBox.addActionListener(listener);

    return _panel;
  }

  @Override
  public void loadFrom(@NotNull XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    myEmulatedCheckBox.setSelected(breakpoint.getProperties().EMULATED);

    myWatchEntryCheckBox.setSelected(breakpoint.getProperties().WATCH_ENTRY);
    myWatchExitCheckBox.setSelected(breakpoint.getProperties().WATCH_EXIT);
  }

  @Override
  public void saveTo(@NotNull XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().EMULATED != myEmulatedCheckBox.isSelected();
    breakpoint.getProperties().EMULATED = myEmulatedCheckBox.isSelected();
    changed = breakpoint.getProperties().WATCH_ENTRY != myWatchEntryCheckBox.isSelected() || changed;
    breakpoint.getProperties().WATCH_ENTRY = myWatchEntryCheckBox.isSelected();
    changed = breakpoint.getProperties().WATCH_EXIT != myWatchExitCheckBox.isSelected() || changed;
    breakpoint.getProperties().WATCH_EXIT = myWatchExitCheckBox.isSelected();
    if (changed) {
      ((XBreakpointBase<?, ?, ?>)breakpoint).fireBreakpointChanged();
    }
  }
}