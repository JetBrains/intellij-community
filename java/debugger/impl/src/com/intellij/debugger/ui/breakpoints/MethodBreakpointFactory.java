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

  @Override
  protected String getHelpID() {
    return HelpID.METHOD_BREAKPOINTS;
  }

  @Override
  public String getDisplayName() {
    return DebuggerBundle.message("method.breakpoints.tab.title");
  }

  @Override
  public BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project) {
    return new MethodBreakpointPropertiesPanel(project);
  }

  @Override
  protected BreakpointPanelAction[] createBreakpointPanelActions(Project project, final DialogWrapper parentDialog) {
    return new BreakpointPanelAction[]{
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
    };
  }

  @Override
  protected void configureBreakpointPanel(BreakpointPanel panel) {
    super.configureBreakpointPanel(panel);
    panel.getTree().setGroupByMethods(false);
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
