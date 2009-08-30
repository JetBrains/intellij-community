/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.HelpID;
import com.intellij.debugger.ui.breakpoints.actions.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.Key;
import org.jdom.Element;

import javax.swing.*;
import java.awt.event.ActionEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: Apr 26, 2005
 */
public class MethodBreakpointFactory extends BreakpointFactory{
  public Breakpoint createBreakpoint(Project project, final Element element) {
    return element.getAttributeValue(WildcardMethodBreakpoint.JDOM_LABEL) != null? new WildcardMethodBreakpoint(project) : new MethodBreakpoint(project);
  }

  public Icon getIcon() {
    return MethodBreakpoint.ICON;
  }

  public Icon getDisabledIcon() {
    return MethodBreakpoint.DISABLED_ICON;
  }

  public BreakpointPanel createBreakpointPanel(Project project, final DialogWrapper parentDialog) {
    BreakpointPanel panel = new BreakpointPanel(project, new MethodBreakpointPropertiesPanel(project), new BreakpointPanelAction[]{
      new SwitchViewAction(),
      new AddWildcardBreakpointAction(project),
      new GotoSourceAction(project) {
        public void actionPerformed(ActionEvent e) {
          super.actionPerformed(e);
          parentDialog.close(DialogWrapper.OK_EXIT_CODE);
        }
      },
      new ViewSourceAction(project),
      new RemoveAction(project),
      new ToggleGroupByClassesAction(),
      new ToggleFlattenPackagesAction(),
    }, getBreakpointCategory(), DebuggerBundle.message("method.breakpoints.tab.title"), HelpID.METHOD_BREAKPOINTS);
    panel.getTree().setGroupByMethods(false);
    return panel;
  }

  public Key<MethodBreakpoint> getBreakpointCategory() {
    return MethodBreakpoint.CATEGORY;
  }

  private static class AddWildcardBreakpointAction extends AddAction {
    private final Project myProject;

    public AddWildcardBreakpointAction(Project project) {
      myProject = project;
    }

    public void actionPerformed(ActionEvent e) {
      AddWildcardBreakpointDialog dialog = new AddWildcardBreakpointDialog(myProject);
      dialog.show();
      if (!dialog.isOK()) {
        return;
      }
      final WildcardMethodBreakpoint methodBreakpoint = DebuggerManagerEx.getInstanceEx(myProject).getBreakpointManager().addMethodBreakpoint(dialog.getClassPattern(), dialog.getMethodName());
      if (methodBreakpoint != null) {
        getPanel().addBreakpoint(methodBreakpoint);
      }
    }
  }
}
