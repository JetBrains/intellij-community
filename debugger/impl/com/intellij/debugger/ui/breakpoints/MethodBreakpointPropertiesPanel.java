/**
 * class MethodBreakpointPropertiesPanel
 * @author Jeka
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class MethodBreakpointPropertiesPanel extends BreakpointPropertiesPanel {
  private JCheckBox myWatchEntryCheckBox;
  private JCheckBox myWatchExitCheckBox;

  public MethodBreakpointPropertiesPanel(final Project project) {
    super(project, MethodBreakpoint.CATEGORY);
  }

  protected JComponent createSpecialBox() {
    JPanel _panel, _panel0;

    myWatchEntryCheckBox = new JCheckBox(DebuggerBundle.message("label.method.breakpoint.properties.panel.method.entry"));
    myWatchExitCheckBox = new JCheckBox(DebuggerBundle.message("label.method.breakpoint.properties.panel.method.exit"));

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
    _panel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), DebuggerBundle.message("label.group.watch.events")));

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

  public void initFrom(Breakpoint breakpoint) {
    super.initFrom(breakpoint);
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

  public void saveTo(Breakpoint breakpoint, Runnable afterUpdate) {
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