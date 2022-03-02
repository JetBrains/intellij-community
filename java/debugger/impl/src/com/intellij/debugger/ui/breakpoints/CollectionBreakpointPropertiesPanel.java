// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui.breakpoints;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.actions.ShowCollectionHistoryAction;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.AnActionLink;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.XDebuggerManager;
import com.intellij.xdebugger.breakpoints.XLineBreakpoint;
import com.intellij.xdebugger.breakpoints.ui.XBreakpointCustomPropertiesPanel;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.java.debugger.breakpoints.properties.JavaCollectionBreakpointProperties;

import javax.swing.*;
import java.awt.*;

@ApiStatus.Experimental
public class CollectionBreakpointPropertiesPanel
  extends XBreakpointCustomPropertiesPanel<XLineBreakpoint<JavaCollectionBreakpointProperties>> {
  private @Nullable String myClsName = null;
  private @Nullable String myFieldName = null;

  @Override
  public @NotNull JComponent getComponent() {
    JPanel panel = new JPanel(new BorderLayout());
    AnActionLink button =
      new AnActionLink(ActionsBundle.message("action.Debugger.ShowCollectionHistory.text"), new MyShowCollectionHistoryAction());
    panel.add(button, BorderLayout.WEST);
    panel.setPreferredSize(new Dimension(panel.getPreferredSize().width, 50));
    return panel;
  }

  @Override
  public void saveTo(@NotNull XLineBreakpoint<JavaCollectionBreakpointProperties> breakpoint) {
  }

  @Override
  public void loadFrom(@NotNull XLineBreakpoint<JavaCollectionBreakpointProperties> breakpoint) {
    myClsName = breakpoint.getProperties().myClassName;
    myFieldName = breakpoint.getProperties().myFieldName;
  }

  private class MyShowCollectionHistoryAction extends AnAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      Project project = getEventProject(e);
      if (project == null) {
        return;
      }
      XDebugSession session = XDebuggerManager.getInstance(project).getCurrentSession();
      if (session == null) {
        return;
      }
      XDebugProcess process = session.getDebugProcess();
      ShowCollectionHistoryAction.CollectionHistoryDialog
        dialog = new ShowCollectionHistoryAction.CollectionHistoryDialog(myClsName, myFieldName, project, process, null);
      dialog.setTitle(JavaDebuggerBundle.message("show.collection.history.dialog.title"));
      dialog.show();
    }
  }
}