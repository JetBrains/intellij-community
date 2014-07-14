/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/**
 * class MethodBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.DialogUtil;
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
  private JCheckBox myWatchEntryCheckBox;
  private JCheckBox myWatchExitCheckBox;

  //public MethodBreakpointPropertiesPanel(final Project project, boolean compact) {
  //  super(project, MethodBreakpoint.CATEGORY, compact);
  //}


  @NotNull
  @Override
  public JComponent getComponent() {
    JPanel _panel, _panel0;

    myWatchEntryCheckBox = new JCheckBox(DebuggerBundle.message("label.method.breakpoint.properties.panel.method.entry"));
    myWatchExitCheckBox = new JCheckBox(DebuggerBundle.message("label.method.breakpoint.properties.panel.method.exit"));
    DialogUtil.registerMnemonic(myWatchEntryCheckBox);
    DialogUtil.registerMnemonic(myWatchExitCheckBox);


    Box watchBox = Box.createVerticalBox();
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
    _panel.setBorder(IdeBorderFactory.createTitledBorder(DebuggerBundle.message("label.group.watch.events"), true));

    ActionListener listener = new ActionListener() {
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
    myWatchEntryCheckBox.setSelected(breakpoint.getProperties().WATCH_ENTRY);
    myWatchExitCheckBox.setSelected(breakpoint.getProperties().WATCH_EXIT);
  }

  @Override
  public void saveTo(@NotNull XBreakpoint<JavaMethodBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().WATCH_ENTRY != myWatchEntryCheckBox.isSelected();
    breakpoint.getProperties().WATCH_ENTRY = myWatchEntryCheckBox.isSelected();
    changed = breakpoint.getProperties().WATCH_EXIT != myWatchExitCheckBox.isSelected() || changed;
    breakpoint.getProperties().WATCH_EXIT = myWatchExitCheckBox.isSelected();
    if (changed) {
      ((XBreakpointBase)breakpoint).fireBreakpointChanged();
    }
  }
}