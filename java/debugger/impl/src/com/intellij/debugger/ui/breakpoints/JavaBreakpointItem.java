/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.SourcePosition;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.popup.util.DetailView;
import com.intellij.xdebugger.breakpoints.ui.BreakpointItem;

import javax.swing.*;

/**
* Created with IntelliJ IDEA.
* User: intendia
* Date: 10.05.12
* Time: 3:16
* To change this template use File | Settings | File Templates.
*/
class JavaBreakpointItem extends BreakpointItem {
  private final Breakpoint myBreakpoint;
  private BreakpointFactory myBreakpointFactory;

  public JavaBreakpointItem(BreakpointFactory breakpointFactory, Breakpoint breakpoint) {
    myBreakpointFactory = breakpointFactory;
    myBreakpoint = breakpoint;
  }

  @Override
  public void setupRenderer(ColoredListCellRenderer renderer, Project project, boolean selected) {
    renderer.setIcon(myBreakpoint.getIcon());
    renderer.append(myBreakpoint.getShortName());
  }

  @Override
  public void updateMnemonicLabel(JLabel label) {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public void execute(Project project, JBPopup popup) {

  }

  @Override
  public String speedSearchText() {
    return myBreakpoint.getDisplayName();
  }

  @Override
  public String footerText() {
    return myBreakpoint.getDisplayName();
  }

  @Override
  public void updateDetailView(DetailView panel) {
    BreakpointPropertiesPanel breakpointPropertiesPanel = myBreakpointFactory
      .createBreakpointPropertiesPanel(myBreakpoint.getProject(), false);
    if (breakpointPropertiesPanel != null) {
      breakpointPropertiesPanel.setSaveOnRemove(true);
    }

    if (breakpointPropertiesPanel != null) {
      breakpointPropertiesPanel.initFrom(myBreakpoint, true);
      final JPanel mainPanel = breakpointPropertiesPanel.getPanel();
      panel.setDetailPanel(mainPanel);
    }
    else {
      panel.setDetailPanel(null);
    }

    if (myBreakpoint instanceof BreakpointWithHighlighter) {
      SourcePosition sourcePosition = ((BreakpointWithHighlighter)myBreakpoint).getSourcePosition();
      VirtualFile virtualFile = sourcePosition.getFile().getVirtualFile();
      showInEditor(panel, virtualFile, sourcePosition.getLine());
    } else {
      panel.clearEditor();
    }
  }

  @Override
  public boolean allowedToRemove() {
    return myBreakpointFactory.breakpointCanBeRemoved(myBreakpoint);
  }

  @Override
  public void removed(Project project) {
    DebuggerManagerEx.getInstanceEx(project).getBreakpointManager().removeBreakpoint(myBreakpoint);
  }

  @Override
  public Object getBreakpoint() {
    return myBreakpoint;
  }

  @Override
  public boolean isEnabled() {
    return myBreakpoint.ENABLED;
  }

  @Override
  public void setEnabled(boolean state) {
    myBreakpoint.ENABLED = state;
  }
}
