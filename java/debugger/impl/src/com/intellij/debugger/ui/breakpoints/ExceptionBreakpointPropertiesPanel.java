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

/*
 * Class ExceptionBreakpointPropertiesPanel
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
import org.jetbrains.java.debugger.breakpoints.properties.JavaExceptionBreakpointProperties;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ExceptionBreakpointPropertiesPanel extends XBreakpointCustomPropertiesPanel<XBreakpoint<JavaExceptionBreakpointProperties>> {
  private JCheckBox myNotifyCaughtCheckBox;
  private JCheckBox myNotifyUncaughtCheckBox;
  //private ExceptionBreakpoint myExceptionBreakpoint;

  //public ExceptionBreakpointPropertiesPanel(Project project, boolean compact) {
  //  super(project, ExceptionBreakpoint.CATEGORY, compact);
  //}

  //protected ClassFilter createClassConditionFilter() {
  //  return null;
  //}

  @NotNull
  @Override
  public JComponent getComponent() {
    myNotifyCaughtCheckBox = new JCheckBox(DebuggerBundle.message("label.exception.breakpoint.properties.panel.caught.exception"));
    myNotifyUncaughtCheckBox = new JCheckBox(DebuggerBundle.message("label.exception.breakpoint.properties.panel.uncaught.exception"));
    DialogUtil.registerMnemonic(myNotifyCaughtCheckBox);
    DialogUtil.registerMnemonic(myNotifyUncaughtCheckBox);


    Box notificationsBox = Box.createVerticalBox();
    JPanel _panel = new JPanel(new BorderLayout());
    _panel.add(myNotifyCaughtCheckBox, BorderLayout.NORTH);
    notificationsBox.add(_panel);
    _panel = new JPanel(new BorderLayout());
    _panel.add(myNotifyUncaughtCheckBox, BorderLayout.NORTH);
    notificationsBox.add(_panel);

    _panel = new JPanel(new BorderLayout());
    JPanel _panel0 = new JPanel(new BorderLayout());
    _panel0.add(notificationsBox, BorderLayout.CENTER);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.WEST);
    _panel0.add(Box.createHorizontalStrut(3), BorderLayout.EAST);
    _panel.add(_panel0, BorderLayout.NORTH);
    _panel.setBorder(IdeBorderFactory.createTitledBorder(
      DebuggerBundle.message("label.exception.breakpoint.properties.panel.group.notifications"), true));

    ActionListener listener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        if (!myNotifyCaughtCheckBox.isSelected() && !myNotifyUncaughtCheckBox.isSelected()) {
          Object source = e.getSource();
          JCheckBox toCheck = null;
          if (myNotifyCaughtCheckBox.equals(source)) {
            toCheck = myNotifyUncaughtCheckBox;
          }
          else if (myNotifyUncaughtCheckBox.equals(source)) {
            toCheck = myNotifyCaughtCheckBox;
          }
          if (toCheck != null) {
            toCheck.setSelected(true);
          }
        }
      }
    };
    myNotifyCaughtCheckBox.addActionListener(listener);
    myNotifyUncaughtCheckBox.addActionListener(listener);
    return _panel;
  }

  //protected void updateCheckboxes() {
  //  super.updateCheckboxes();
  //  myPassCountCheckbox.setEnabled(!(myExceptionBreakpoint instanceof AnyExceptionBreakpoint));
  //}

  @Override
  public void loadFrom(@NotNull XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    myNotifyCaughtCheckBox.setSelected(breakpoint.getProperties().NOTIFY_CAUGHT);
    myNotifyUncaughtCheckBox.setSelected(breakpoint.getProperties().NOTIFY_UNCAUGHT);
  }

  @Override
  public void saveTo(@NotNull XBreakpoint<JavaExceptionBreakpointProperties> breakpoint) {
    boolean changed = breakpoint.getProperties().NOTIFY_CAUGHT != myNotifyCaughtCheckBox.isSelected();
    breakpoint.getProperties().NOTIFY_CAUGHT = myNotifyCaughtCheckBox.isSelected();
    changed = breakpoint.getProperties().NOTIFY_UNCAUGHT != myNotifyUncaughtCheckBox.isSelected() || changed;
    breakpoint.getProperties().NOTIFY_UNCAUGHT = myNotifyUncaughtCheckBox.isSelected();
    if (changed) {
      ((XBreakpointBase)breakpoint).fireBreakpointChanged();
    }
  }
}