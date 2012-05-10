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
package com.intellij.debugger.ui.breakpoints.actions;

import com.intellij.debugger.ui.breakpoints.Breakpoint;
import com.intellij.debugger.ui.breakpoints.BreakpointFactory;
import com.intellij.debugger.ui.breakpoints.BreakpointPanel;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

/**
 * @author Eugene Zhuravlev
 *         Date: May 25, 2005
 */
public class AddAction extends BreakpointPanelAction {
  protected final Project myProject;
  protected BreakpointFactory myBreakpointFactory;

  public AddAction(BreakpointFactory breakpointFactory, Project project) {
    super(IdeBundle.message("button.add"));
    myProject = project;
    this.myBreakpointFactory = breakpointFactory;
  }

  public void setPanel(BreakpointPanel panel) {
    super.setPanel(panel);
    getPanel().getTable().registerKeyboardAction(this, KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
  }

  public void update() {
  }

  public void actionPerformed(ActionEvent e) {
    Breakpoint breakpoint = myBreakpointFactory.addBreakpoint(myProject);
    if (breakpoint != null) {
      getPanel().addBreakpoint(breakpoint);
    }
  }
}
