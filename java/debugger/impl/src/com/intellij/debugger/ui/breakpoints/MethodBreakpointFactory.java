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
import com.intellij.icons.AllIcons;
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
    return AllIcons.Debugger.Db_method_breakpoint;
  }

  public Icon getDisabledIcon() {
    return AllIcons.Debugger.Db_disabled_method_breakpoint;
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
  public BreakpointPropertiesPanel createBreakpointPropertiesPanel(Project project, boolean compact) {
    return new MethodBreakpointPropertiesPanel(project, compact);
  }

  @Override
  protected BreakpointPanelAction[] createBreakpointPanelActions(Project project, final DialogWrapper parentDialog) {
    return new BreakpointPanelAction[]{
      new SwitchViewAction(),
      new AddAction(this, project),
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

  @Override
  public boolean canAddBreakpoints() {
    return true;
  }

  public WildcardMethodBreakpoint addBreakpoint(Project project) {
    AddWildcardBreakpointDialog dialog = new AddWildcardBreakpointDialog(project);
    dialog.show();
    WildcardMethodBreakpoint methodBreakpoint;
    methodBreakpoint = !dialog.isOK()
                       ? null
                       : DebuggerManagerEx.getInstanceEx(project).getBreakpointManager()
                         .addMethodBreakpoint(dialog.getClassPattern(), dialog.getMethodName());
    return methodBreakpoint;
  }
}
