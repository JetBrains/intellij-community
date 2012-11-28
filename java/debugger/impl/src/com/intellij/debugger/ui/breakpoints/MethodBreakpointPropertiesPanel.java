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
import com.intellij.openapi.project.Project;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.util.ui.DialogUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MethodBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  private JCheckBox myWatchEntryCheckBox;
  private JCheckBox myWatchExitCheckBox;

  public MethodBreakpointPropertiesPanel(final Project project, boolean compact) {
    super(project, MethodBreakpoint.CATEGORY, compact);
  }

  protected JComponent createSpecialBox() {
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

  public void initFrom(Breakpoint breakpoint, boolean moreOptionsVisible) {
    super.initFrom(breakpoint, moreOptionsVisible);
    if (breakpoint instanceof MethodBreakpoint) {
      MethodBreakpoint methodBreakpoint = (MethodBreakpoint)breakpoint;
      myWatchEntryCheckBox.setSelected(methodBreakpoint.WATCH_ENTRY);
      myWatchExitCheckBox.setSelected(methodBreakpoint.WATCH_EXIT);
    }
    else if (breakpoint instanceof WildcardMethodBreakpoint){
      final WildcardMethodBreakpoint methodBreakpoint = ((WildcardMethodBreakpoint)breakpoint);
      myWatchEntryCheckBox.setSelected(methodBreakpoint.WATCH_ENTRY);
      myWatchExitCheckBox.setSelected(methodBreakpoint.WATCH_EXIT);
    }
  }

  public void saveTo(Breakpoint breakpoint, @NotNull Runnable afterUpdate) {
    if (breakpoint instanceof MethodBreakpoint) {
      MethodBreakpoint methodBreakpoint = (MethodBreakpoint)breakpoint;
      methodBreakpoint.WATCH_ENTRY = myWatchEntryCheckBox.isSelected();
      methodBreakpoint.WATCH_EXIT = myWatchExitCheckBox.isSelected();
    }
    else if (breakpoint instanceof WildcardMethodBreakpoint){
      final WildcardMethodBreakpoint methodBreakpoint = ((WildcardMethodBreakpoint)breakpoint);
      methodBreakpoint.WATCH_ENTRY = myWatchEntryCheckBox.isSelected();
      methodBreakpoint.WATCH_EXIT = myWatchExitCheckBox.isSelected();
    }
    super.saveTo(breakpoint, afterUpdate);
  }
}